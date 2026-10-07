package az.millikart.directory.service;

import az.millikart.common.audit.AuditAction;
import az.millikart.common.audit.AuditEntity;
import az.millikart.common.audit.AuditEvent;
import az.millikart.common.audit.AuditLog;
import az.millikart.common.audit.AuditLogService;
import az.millikart.common.audit.AuditOutcome;
import az.millikart.common.dto.PagedResponse;
import az.millikart.common.exception.BusinessException;
import az.millikart.common.exception.InvalidStateException;
import az.millikart.common.search.SearchTerms;
import az.millikart.common.security.Role;
import az.millikart.common.security.UserPrincipal;
import az.millikart.directory.dto.AuditLogFilter;
import az.millikart.directory.dto.AuditLogResponse;
import az.millikart.directory.repository.AuditLogQueryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Журнал пишут все сервисы через common, читает только этот (Р-41).
@Service
public class AuditLogQueryService {

    // Потолок выгрузки (Р-137): больше — отказ с просьбой сузить фильтры, а не молча обрезанный файл.
    public static final int MAX_EXPORT_ROWS = 100_000;
    private static final int EXPORT_PAGE = 1_000;

    // «Требует внимания» (Р-137): неподтверждённое (деньги с неизвестным исходом, перерыв журнала) и признаки атаки
    // на вход — кража refresh-токена, блокировка учётки, лимит попыток по адресу.
    private static final Set<String> ATTENTION_ACTIONS =
            Set.of(AuditAction.TOKEN_REUSE, AuditAction.LOCKOUT, AuditAction.RATE_LIMIT);

    private static final String CSV_HEADER =
            "createdAt;action;outcome;performedBy;clientIp;entityType;entityId;companyId;details;traceId;id";

    private final AuditLogQueryRepository auditLogQueryRepository;
    private final AuditLogService auditLogService;
    // Страницы выгрузки — запросом без подсчёта: у репозитория один метод, и он считает строки на каждой (Р-42).
    private final EntityManager entityManager;

    public AuditLogQueryService(AuditLogQueryRepository auditLogQueryRepository,
                                AuditLogService auditLogService,
                                EntityManager entityManager) {
        this.auditLogQueryRepository = auditLogQueryRepository;
        this.auditLogService = auditLogService;
        this.entityManager = entityManager;
    }

    // Фильтры и страницы — в базе: журнал неограничен (P2-2). entityType — точное равенство в верхнем
    // регистре (писатели хранят константы AuditEntity): IgnoreCase убьёт индекс idx_audit_logs_entity.
    @Transactional(readOnly = true)
    public PagedResponse<AuditLogResponse> listAuditLogs(AuditLogFilter request, Pageable pageable,
                                                         UserPrincipal principal) {
        String companyScope = companyScopeOf(request, principal, AuditAction.LIST, "list audit logs");

        // id обязателен: без него записи с равным временем прыгают между страницами (P3-1). Индекс
        // idx_audit_logs_created намеренно только по created_at: менять лишь по замеру медленного запроса.
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        Page<AuditLog> page = auditLogQueryRepository.findAll(specOf(request, companyScope, request.to()), newestFirst);
        return PagedResponse.of(page, page.getContent().stream().map(AuditLogQueryService::mapToResponse).toList());
    }

    // Выгрузка (Р-137) — те же права и фильтры, что у списка. Верхняя граница прижата к моменту запроса: записи,
    // появившиеся во время выгрузки, не сдвигают её страницы. Сама выгрузка — запись в журнале (PCI DSS 10.2.1.3).
    public ExportPlan planExport(AuditLogFilter request, UserPrincipal principal) {
        String companyScope = companyScopeOf(request, principal, AuditAction.EXPORT, "export audit logs");
        Instant now = Instant.now();
        Instant upTo = request.to() == null || request.to().isAfter(now) ? now : request.to();
        Specification<AuditLog> spec = specOf(request, companyScope, upTo);
        long total = auditLogQueryRepository.findAll(spec, PageRequest.of(0, 1)).getTotalElements();
        if (total > MAX_EXPORT_ROWS) {
            throw new BusinessException("The export is limited to " + MAX_EXPORT_ROWS + " records, the filters match "
                    + total + "; narrow the period or the filters");
        }
        auditLogService.recordSuccess(AuditEvent.of(AuditEntity.AUDIT_LOG, "ALL", AuditAction.EXPORT,
                UserPrincipal.getUsername(principal), UserPrincipal.getCompanyId(principal),
                "Exported " + total + " records up to " + upTo + describe(request, companyScope)));
        return new ExportPlan(spec, total);
    }

    // Старые раньше: новые записи ложатся в конец и не сдвигают уже прочитанные страницы.
    public void writeCsv(ExportPlan plan, Writer out) throws IOException {
        out.write(CSV_HEADER);
        out.write("\r\n");
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        for (int offset = 0; offset < plan.total(); offset += EXPORT_PAGE) {
            CriteriaQuery<AuditLog> query = cb.createQuery(AuditLog.class);
            Root<AuditLog> root = query.from(AuditLog.class);
            Predicate where = plan.spec().toPredicate(root, query, cb);
            if (where != null) {
                query.where(where);
            }
            query.orderBy(cb.asc(root.get("createdAt")), cb.asc(root.get("id")));
            List<AuditLog> page = entityManager.createQuery(query)
                    .setFirstResult(offset).setMaxResults(EXPORT_PAGE).getResultList();
            for (AuditLog log : page) {
                out.write(csvLine(log));
            }
            out.flush();
        }
    }

    public record ExportPlan(Specification<AuditLog> spec, long total) {
    }

    // Фильтр компании выбирают только глобальные читатели; у руководителя и менеджера скоуп — своя
    // компания, присланный companyId его не меняет. null — все компании.
    private String companyScopeOf(AuditLogFilter request, UserPrincipal principal, String action, String attempt) {
        Role actorRole = UserPrincipal.getRole(principal);
        String actorCompanyId = UserPrincipal.getCompanyId(principal);
        String actorUsername = UserPrincipal.getUsername(principal);
        if (actorRole == Role.SYSTEM_ADMIN || actorRole == Role.AUDITOR) {
            return request.companyId() != null && !request.companyId().isBlank() ? request.companyId().trim() : null;
        }
        if (actorRole == Role.COMPANY_HEAD || actorRole == Role.COMPANY_MANAGER) {
            if (actorCompanyId == null) {
                auditLogService.logDenied(AuditEntity.AUDIT_LOG, "ALL", action, actorUsername, null,
                        "Denied: " + actorRole + " without a company attempted to " + attempt);
                throw new InvalidStateException("Access denied: User not assigned to a company");
            }
            return actorCompanyId;
        }
        auditLogService.logDenied(AuditEntity.AUDIT_LOG, "ALL", action, actorUsername, actorCompanyId,
                "Denied: role " + UserPrincipal.getRawRole(principal) + " attempted to " + attempt);
        throw new InvalidStateException("Access denied");
    }

    private static Specification<AuditLog> specOf(AuditLogFilter request, String companyScope, Instant to) {
        // action — тоже константы AuditAction в верхнем регистре, точное равенство.
        String entityId = request.entityId() != null && !request.entityId().isBlank() ? request.entityId().trim() : null;
        return filter(companyScope, upperOrNull(request.entityType()), entityId,
                SearchTerms.toLikePattern(request.search()), request.outcome(), request.from(), to,
                upperOrNull(request.action()), SearchTerms.toLikePattern(request.performedBy()), request.attention());
    }

    // Specification, а не JPQL с :param IS NULL: на связывании типизированных null запросы к PostgreSQL
    // ломаются, а так отсутствующий фильтр вообще не попадает в SQL.
    private static Specification<AuditLog> filter(String companyId, String entityType,
                                                  String entityId, String searchPattern,
                                                  AuditOutcome outcome, Instant from, Instant to,
                                                  String action, String performedByPattern, boolean attention) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (attention) {
                where.add(cb.or(cb.equal(root.get("outcome"), AuditOutcome.UNRESOLVED),
                        root.get("action").in(ATTENTION_ACTIONS)));
            }
            if (action != null) {
                where.add(cb.equal(root.get("action"), action));
            }
            if (performedByPattern != null) {
                where.add(cb.like(cb.lower(root.get("performedBy")), cb.lower(cb.literal(performedByPattern)),
                        SearchTerms.LIKE_ESCAPE));
            }
            if (companyId != null) {
                where.add(cb.equal(root.get("companyId"), companyId));
            }
            if (entityType != null) {
                where.add(cb.equal(root.get("entityType"), entityType));
            }
            if (entityId != null) {
                where.add(cb.equal(cb.lower(root.get("entityId")), entityId.toLowerCase(Locale.ROOT)));
            }
            if (outcome != null) {
                where.add(cb.equal(root.get("outcome"), outcome));
            }
            if (from != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                where.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            if (searchPattern != null) {
                // Обе стороны понижает база, как во всех поисках (SearchTerms.toLikePattern).
                Expression<String> pattern = cb.lower(cb.literal(searchPattern));
                where.add(cb.or(
                        cb.like(cb.lower(root.get("performedBy")), pattern, SearchTerms.LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("action")), pattern, SearchTerms.LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("entityId")), pattern, SearchTerms.LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("details")), pattern, SearchTerms.LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("traceId")), pattern, SearchTerms.LIKE_ESCAPE)));
            }
            return cb.and(where.toArray(new Predicate[0]));
        };
    }

    // Фильтры выгрузки — в запись журнала: проверяющий видит, что именно унесли.
    private static String describe(AuditLogFilter request, String companyScope) {
        StringBuilder filters = new StringBuilder();
        append(filters, "companyId", companyScope);
        append(filters, "entityType", request.entityType());
        append(filters, "entityId", request.entityId());
        append(filters, "action", request.action());
        append(filters, "outcome", request.outcome() != null ? request.outcome().name() : null);
        append(filters, "performedBy", request.performedBy());
        append(filters, "search", request.search());
        append(filters, "from", request.from() != null ? request.from().toString() : null);
        if (request.attention()) {
            append(filters, "attention", "true");
        }
        return filters.isEmpty() ? ", no filters" : ", filters: " + filters;
    }

    private static void append(StringBuilder filters, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!filters.isEmpty()) {
            filters.append(", ");
        }
        filters.append(name).append('=').append(value);
    }

    private static String csvLine(AuditLog log) {
        return String.join(";",
                cell(log.getCreatedAt() != null ? log.getCreatedAt().toString() : null),
                cell(log.getAction()),
                cell(log.getOutcome() != null ? log.getOutcome().name() : null),
                cell(log.getPerformedBy()),
                cell(log.getClientIp()),
                cell(log.getEntityType()),
                cell(log.getEntityId()),
                cell(log.getCompanyId()),
                cell(log.getDetails()),
                cell(log.getTraceId()),
                cell(log.getId() != null ? log.getId().toString() : null)) + "\r\n";
    }

    // Разделитель — точка с запятой: так файл открывает Excel с русской и азербайджанской локалью. Значение, с
    // которого Excel начал бы формулу (=, +, -, @), — с апострофом: логин неудачного входа — чужой ввод (CSV-инъекция).
    static String cell(String value) {
        if (value == null) {
            return "";
        }
        String safe = !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        if (safe.contains(";") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private static String upperOrNull(String value) {
        return value != null && !value.isBlank() ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private static AuditLogResponse mapToResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getEntityType(),
                log.getEntityId(),
                log.getAction(),
                log.getPerformedBy(),
                log.getCompanyId(),
                log.getDetails(),
                log.getClientIp(),
                log.getOutcome(),
                log.getCreatedAt(),
                log.getTraceId()
        );
    }
}
