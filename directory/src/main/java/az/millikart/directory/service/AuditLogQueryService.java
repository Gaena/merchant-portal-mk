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
import az.millikart.directory.dto.AuditLogResponse;
import az.millikart.directory.repository.AuditLogQueryRepository;
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

// Читающая половина журнала осталась в directory, когда пишущая уехала в common (Р-41):
// пишут все сервисы, показывает только этот.
@Service
public class AuditLogQueryService {

    private final AuditLogQueryRepository auditLogQueryRepository;
    private final AuditLogService auditLogService;

    public AuditLogQueryService(AuditLogQueryRepository auditLogQueryRepository,
                                AuditLogService auditLogService) {
        this.auditLogQueryRepository = auditLogQueryRepository;
        this.auditLogService = auditLogService;
    }

    // Фильтрация и страницы — в базе: журнал append-only и неограничен, загрузка его в память была
    // P2-2. entityType сравнивается точным равенством по значению в верхнем регистре (все писатели
    // хранят константы AuditEntity): «улучшение» до IgnoreCase убьёт индекс idx_audit_logs_entity.
    // Фильтры независимы и необязательны (до P3-1 entityType без entityId игнорировался, D.1).
    @Transactional(readOnly = true)
    public PagedResponse<AuditLogResponse> listAuditLogs(String entityType, String entityId,
                                                         String search, AuditOutcome outcome,
                                                         Instant from, Instant to,
                                                         Pageable pageable, UserPrincipal principal) {
        Role actorRole = UserPrincipal.getRole(principal);
        String actorCompanyId = UserPrincipal.getCompanyId(principal);
        String actorUsername = UserPrincipal.getUsername(principal);

        String canonicalEntityType = entityType != null && !entityType.isBlank()
                ? entityType.trim().toUpperCase(Locale.ROOT) : null;
        String cleanEntityId = entityId != null && !entityId.isBlank() ? entityId.trim() : null;

        String companyScope;
        if (actorRole == Role.SYSTEM_ADMIN || actorRole == Role.AUDITOR) {
            companyScope = null;
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

        // D.3: createdAt DESC + id DESC — уникальный довесок обязателен, иначе записи с равным
        // временем прыгают между страницами (P3-1). D.4: idx_audit_logs_created намеренно оставлен
        // по одному created_at; пересматривать только по реально замеренному медленному запросу.
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        Page<AuditLog> page = auditLogQueryRepository.findAll(
                filter(companyScope, canonicalEntityType, cleanEntityId,
                        SearchTerms.toLikePattern(search), outcome, from, to),
                newestFirst);

        return PagedResponse.of(page, page.getContent().stream().map(AuditLogQueryService::mapToResponse).toList());
    }

    // Необязательные фильтры собраны Specification'ами: отсутствующий фильтр вообще не попадает
    // в SQL — потому это не один JPQL с проверками :param IS NULL (на связывании типизированных
    // null'ов запросы к PostgreSQL и ломаются). Каждый LIKE идёт по шаблону из
    // SearchTerms.toLikePattern, escape обязан совпадать с SearchTerms.LIKE_ESCAPE.
    private static Specification<AuditLog> filter(String companyId, String entityType,
                                                  String entityId, String searchPattern,
                                                  AuditOutcome outcome, Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
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
                where.add(cb.or(
                        cb.like(cb.lower(root.get("performedBy")), searchPattern, SearchTerms.LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("action")), searchPattern, SearchTerms.LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("entityId")), searchPattern, SearchTerms.LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("details")), searchPattern, SearchTerms.LIKE_ESCAPE)));
            }
            return cb.and(where.toArray(new Predicate[0]));
        };
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
