package az.millikart.directory.controller;

import az.millikart.common.audit.AuditOutcome;
import az.millikart.common.dto.PagedResponse;
import az.millikart.common.search.SearchTerms;
import az.millikart.common.security.UserPrincipal;
import az.millikart.directory.dto.AuditIntegrityReport;
import az.millikart.directory.dto.AuditLogFilter;
import az.millikart.directory.dto.AuditLogResponse;
import az.millikart.directory.service.AuditIntegrityService;
import az.millikart.directory.service.AuditLogQueryService;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogQueryService auditLogQueryService;
    private final AuditIntegrityService auditIntegrityService;

    public AuditLogController(AuditLogQueryService auditLogQueryService, AuditIntegrityService auditIntegrityService) {
        this.auditLogQueryService = auditLogQueryService;
        this.auditIntegrityService = auditIntegrityService;
    }

    // Проверка цепочки журнала (Р-138): POST, потому что сама пишет запись VERIFY в журнал.
    @PostMapping("/integrity-checks")
    public AuditIntegrityReport verifyIntegrity(@AuthenticationPrincipal UserPrincipal principal) {
        return auditIntegrityService.verify(principal);
    }

    // Потолок страницы: таблица только растёт, размер без предела вытянет её в heap одним запросом.
    private static final int MAX_PAGE_SIZE = 200;

    private static final DateTimeFormatter EXPORT_FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    // Параметры приводятся, а не отвергаются: PageRequest.of бросает на page < 0 и size < 1, и клиент
    // получил бы 500.
    @GetMapping
    public PagedResponse<AuditLogResponse> list(
            @RequestParam(value = "entityType", required = false) String entityType,
            @RequestParam(value = "entityId", required = false) String entityId,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "outcome", required = false) String outcome,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "performedBy", required = false) String performedBy,
            @RequestParam(value = "companyId", required = false) String companyId,
            @RequestParam(value = "attention", required = false) String attention,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE));
        return auditLogQueryService.listAuditLogs(
                filterOf(entityType, entityId, search, outcome, from, to, action, performedBy, companyId, attention),
                pageable, principal);
    }

    // CSV с теми же фильтрами (Р-137). Отказ — до первого байта: права, потолок строк; дальше файл идёт потоком.
    // BOM — чтобы Excel прочёл UTF-8, а не кодовую страницу системы.
    @GetMapping("/export")
    public void export(
            @RequestParam(value = "entityType", required = false) String entityType,
            @RequestParam(value = "entityId", required = false) String entityId,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "outcome", required = false) String outcome,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "performedBy", required = false) String performedBy,
            @RequestParam(value = "companyId", required = false) String companyId,
            @RequestParam(value = "attention", required = false) String attention,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletResponse response) throws IOException {
        AuditLogQueryService.ExportPlan plan = auditLogQueryService.planExport(
                filterOf(entityType, entityId, search, outcome, from, to, action, performedBy, companyId, attention),
                principal);
        String fileName = "audit-log-" + EXPORT_FILE_TIME.format(Instant.now()) + ".csv";
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
        Writer out = response.getWriter();
        out.write('\uFEFF');
        auditLogQueryService.writeCsv(plan, out);
        out.flush();
    }

    private static AuditLogFilter filterOf(String entityType, String entityId, String search, String outcome,
                                           String from, String to, String action, String performedBy,
                                           String companyId, String attention) {
        return new AuditLogFilter(entityType, entityId, SearchTerms.normalize(search), parseOutcome(outcome),
                parseInstant(from, false), parseInstant(to, true), action, SearchTerms.normalize(performedBy),
                companyId, "true".equalsIgnoreCase(attention != null ? attention.trim() : null));
    }

    // Незнакомое значение — «нет фильтра», а не 400.
    private static AuditOutcome parseOutcome(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AuditOutcome.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknownValue) {
            return null;
        }
    }

    // Голая дата — целые UTC-сутки; нераспознанное — «нет фильтра», а не 400.
    private static Instant parseInstant(String raw, boolean endOfDay) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.trim();
        try {
            return Instant.parse(cleaned);
        } catch (DateTimeParseException notAnInstant) {
        }
        try {
            LocalDate day = LocalDate.parse(cleaned);
            return endOfDay
                    ? day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1)
                    : day.atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException notADate) {
            return null;
        }
    }
}
