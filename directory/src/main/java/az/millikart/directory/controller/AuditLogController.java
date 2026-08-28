package az.millikart.directory.controller;

import az.millikart.common.audit.AuditOutcome;
import az.millikart.common.dto.PagedResponse;
import az.millikart.common.search.SearchTerms;
import az.millikart.common.security.UserPrincipal;
import az.millikart.directory.dto.AuditLogResponse;
import az.millikart.directory.service.AuditLogQueryService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogQueryService auditLogQueryService;

    public AuditLogController(AuditLogQueryService auditLogQueryService) {
        this.auditLogQueryService = auditLogQueryService;
    }

    // Потолок страницы: таблица только растёт, размер без предела вытянет её в heap одним запросом.
    private static final int MAX_PAGE_SIZE = 200;

    // Каждый параметр приводится, а не отвергается: PageRequest.of бросает на page < 0 и size < 1,
    // а IllegalArgumentException из опечатки в query-параметре возвращается клиенту как 500.
    // Дефолты page/size совпадают со списком транзакций в pbl. Фильтры независимы и необязательны:
    // entityType в одиночку работает с P3-1 (D.1).
    @GetMapping
    public PagedResponse<AuditLogResponse> list(
            @RequestParam(value = "entityType", required = false) String entityType,
            @RequestParam(value = "entityId", required = false) String entityId,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "outcome", required = false) String outcome,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return auditLogQueryService.listAuditLogs(entityType, entityId,
                SearchTerms.normalize(search), parseOutcome(outcome),
                parseInstant(from, false), parseInstant(to, true),
                pageable, principal);
    }

    // Обрезается и поднимается в верхний регистр; значение, которого AuditOutcome не знает,
    // схлопывается в «нет фильтра», а не в 400.
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

    // Принимает ISO-8601 instant (2026-08-24T10:15:30Z) или голую ISO-дату (2026-08-24); голая дата
    // значит целые UTC-сутки: начало для from, последняя наносекунда для to. Всё прочее —
    // «нет фильтра», не 400.
    private static Instant parseInstant(String raw, boolean endOfDay) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.trim();
        try {
            return Instant.parse(cleaned);
        } catch (DateTimeParseException notAnInstant) {
            // дальше — попытка разобрать как голую дату
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
