package az.millikart.directory.service;

import az.millikart.common.audit.AuditAction;
import az.millikart.common.audit.AuditEntity;
import az.millikart.common.audit.AuditLog;
import az.millikart.common.audit.AuditLogService;
import az.millikart.common.audit.AuditOutcome;
import az.millikart.common.dto.PagedResponse;
import az.millikart.common.exception.InvalidStateException;
import az.millikart.common.search.SearchTerms;
import az.millikart.common.security.Role;
import az.millikart.common.security.UserPrincipal;
import az.millikart.directory.dto.AuditLogFilter;
import az.millikart.directory.dto.AuditLogResponse;
import az.millikart.directory.repository.AuditLogQueryRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    private final AuditLogQueryRepository auditLogQueryRepository;
    private final AuditLogService auditLogService;

    public AuditLogQueryService(AuditLogQueryRepository auditLogQueryRepository,
                                AuditLogService auditLogService) {
        this.auditLogQueryRepository = auditLogQueryRepository;
        this.auditLogService = auditLogService;
    }

    // Фильтры и страницы — в базе: журнал неограничен (P2-2). entityType — точное равенство в верхнем
    // регистре (писатели хранят константы AuditEntity): IgnoreCase убьёт индекс idx_audit_logs_entity.
    @Transactional(readOnly = true)
    public PagedResponse<AuditLogResponse> listAuditLogs(AuditLogFilter request, Pageable pageable,
                                                         UserPrincipal principal) {
        Role actorRole = UserPrincipal.getRole(principal);
        String actorCompanyId = UserPrincipal.getCompanyId(principal);
        String actorUsername = UserPrincipal.getUsername(principal);

        // action — тоже константы AuditAction в верхнем регистре, точное равенство.
        String canonicalEntityType = upperOrNull(request.entityType());
        String canonicalAction = upperOrNull(request.action());
        String cleanEntityId = request.entityId() != null && !request.entityId().isBlank() ? request.entityId().trim() : null;

        // Фильтр компании выбирают только глобальные читатели; у руководителя и менеджера скоуп — своя
        // компания, присланный companyId его не меняет.
        String companyScope;
        if (actorRole == Role.SYSTEM_ADMIN || actorRole == Role.AUDITOR) {
            companyScope = request.companyId() != null && !request.companyId().isBlank() ? request.companyId().trim() : null;
        } else if (actorRole == Role.COMPANY_HEAD || actorRole == Role.COMPANY_MANAGER) {
            if (actorCompanyId == null) {
                auditLogService.logDenied(AuditEntity.AUDIT_LOG, "ALL", AuditAction.LIST, actorUsername, null,
                        "Denied: " + actorRole + " without a company attempted to list audit logs");
                throw new InvalidStateException("Access denied: User not assigned to a company");
            }
            companyScope = actorCompanyId;
        } else {
            auditLogService.logDenied(AuditEntity.AUDIT_LOG, "ALL", AuditAction.LIST, actorUsername, actorCompanyId,
                    "Denied: role " + UserPrincipal.getRawRole(principal)
                            + " attempted to list audit logs");
            throw new InvalidStateException("Access denied");
        }

        // id обязателен: без него записи с равным временем прыгают между страницами (P3-1). Индекс
        // idx_audit_logs_created намеренно только по created_at: менять лишь по замеру медленного запроса.
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        Page<AuditLog> page = auditLogQueryRepository.findAll(
                filter(companyScope, canonicalEntityType, cleanEntityId, SearchTerms.toLikePattern(request.search()),
                        request.outcome(), request.from(), request.to(), canonicalAction,
                        SearchTerms.toLikePattern(request.performedBy())),
                newestFirst);

        return PagedResponse.of(page, page.getContent().stream().map(AuditLogQueryService::mapToResponse).toList());
    }

    // Specification, а не JPQL с :param IS NULL: на связывании типизированных null запросы к PostgreSQL
    // ломаются, а так отсутствующий фильтр вообще не попадает в SQL.
    private static Specification<AuditLog> filter(String companyId, String entityType,
                                                  String entityId, String searchPattern,
                                                  AuditOutcome outcome, Instant from, Instant to,
                                                  String action, String performedByPattern) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
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
                        cb.like(cb.lower(root.get("details")), pattern, SearchTerms.LIKE_ESCAPE)));
            }
            return cb.and(where.toArray(new Predicate[0]));
        };
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
                log.getCreatedAt()
        );
    }
}
