package az.millikart.directory.service;

import az.millikart.common.audit.AuditAction;
import az.millikart.common.audit.AuditChain;
import az.millikart.common.audit.AuditEntity;
import az.millikart.common.audit.AuditEvent;
import az.millikart.common.audit.AuditLog;
import az.millikart.common.audit.AuditLogService;
import az.millikart.common.exception.InvalidStateException;
import az.millikart.common.security.Role;
import az.millikart.common.security.UserPrincipal;
import az.millikart.directory.dto.AuditIntegrityReport;
import az.millikart.directory.dto.AuditIntegrityReport.Problem;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Проверка цепочки журнала (Р-138, PCI DSS 10.3.4): звенья по порядку, у каждого — запись, HMAC от звена перед ним
// и время записи рядом с моментом звена. Разрыв — ERROR с маркером для мониторинга и запись UNRESOLVED: она
// попадает в «Требует внимания» (Р-137). Цепочка общая для всех компаний — проверяют SYSTEM_ADMIN и AUDITOR.
@Service
public class AuditIntegrityService {

    private static final Logger log = LoggerFactory.getLogger(AuditIntegrityService.class);

    // Маркер для мониторинга: цепочка журнала разорвана — журнал правили мимо приложения.
    public static final String AUDIT_CHAIN_BROKEN_MARKER = "AUDIT_CHAIN_BROKEN";

    private static final int PAGE = 1_000;
    private static final int MAX_PROBLEMS = 20;
    // created_at ставит Hibernate при вставке, момент звена — AuditChain в той же транзакции: расходятся на
    // миллисекунды. Пять минут — запас на долгую транзакцию, а не на правку времени записи.
    private static final Duration SEAL_TOLERANCE = Duration.ofMinutes(5);

    private final EntityManager entityManager;
    private final AuditChain chain;
    private final AuditLogService auditLogService;

    public AuditIntegrityService(EntityManager entityManager, AuditChain chain, AuditLogService auditLogService) {
        this.entityManager = entityManager;
        this.chain = chain;
        this.auditLogService = auditLogService;
    }

    public AuditIntegrityReport verify(UserPrincipal principal) {
        Role role = UserPrincipal.getRole(principal);
        if (role != Role.SYSTEM_ADMIN && role != Role.AUDITOR) {
            auditLogService.logDenied(AuditEntity.AUDIT_LOG, "ALL", AuditAction.VERIFY, UserPrincipal.getUsername(principal),
                    UserPrincipal.getCompanyId(principal), "Denied: role " + UserPrincipal.getRawRole(principal)
                            + " attempted to verify the audit chain");
            throw new InvalidStateException("Access denied");
        }
        return verifyAndRecord(UserPrincipal.getUsername(principal));
    }

    // Ночная проверка — от system.
    public AuditIntegrityReport verifyScheduled() {
        return verifyAndRecord(null);
    }

    private AuditIntegrityReport verifyAndRecord(String actor) {
        AuditIntegrityReport report = check();
        // Голова — в лог при каждой проверке: лог живёт отдельно от базы, и усечение хвоста цепочки вместе с
        // откатом головы видно по уменьшившемуся номеру.
        log.info("Audit chain head seq={} after checking {} records, intact={}", report.headSeq(),
                report.checkedRecords(), report.intact());
        if (report.intact()) {
            auditLogService.recordSuccess(AuditEvent.of(AuditEntity.AUDIT_LOG, "ALL", AuditAction.VERIFY, actor, null,
                    "Audit chain intact: " + report.checkedRecords() + " records checked up to seq " + report.headSeq()
                            + (report.notCovered() > 0 ? ", " + report.notCovered() + " records predate the chain" : "")));
        } else {
            String summary = summary(report);
            log.error("{}: {}", AUDIT_CHAIN_BROKEN_MARKER, summary);
            auditLogService.logUnresolved(AuditEntity.AUDIT_LOG, "ALL", AuditAction.VERIFY, actor, null,
                    "Audit chain broken: " + summary);
        }
        return report;
    }

    private AuditIntegrityReport check() {
        Object[] head = (Object[]) entityManager.createNativeQuery(
                "SELECT last_seq, last_hash FROM audit_chain_head WHERE id = 1").getSingleResult();
        long headSeq = ((Number) head[0]).longValue();
        String headHash = (String) head[1];

        List<Problem> problems = new ArrayList<>();
        int problemCount = 0;
        String previous = AuditChain.GENESIS;
        long expected = 1;
        long checked = 0;
        long linkedRecords = 0;
        Instant chainStart = null;
        while (true) {
            @SuppressWarnings("unchecked")
            List<Object[]> links = entityManager.createNativeQuery("SELECT seq, audit_id, sealed_at_micros, hash "
                            + "FROM audit_chain WHERE seq >= :from AND seq <= :headSeq ORDER BY seq")
                    .setParameter("from", expected)
                    .setParameter("headSeq", headSeq)
                    .setMaxResults(PAGE)
                    .getResultList();
            if (links.isEmpty()) {
                break;
            }
            Map<UUID, AuditLog> records = recordsOf(links);
            for (Object[] link : links) {
                long seq = ((Number) link[0]).longValue();
                UUID auditId = toUuid(link[1]);
                long sealedAt = ((Number) link[2]).longValue();
                String stored = (String) link[3];
                AuditLog record = records.get(auditId);
                Problem problem = null;
                if (seq != expected) {
                    problem = new Problem("LINKS_MISSING", seq, auditId,
                            "links " + expected + "–" + (seq - 1) + " are missing");
                } else if (record == null) {
                    problem = new Problem("RECORD_DELETED", seq, auditId, "the record of this link is gone");
                } else if (!chain.hash(previous, AuditChain.canonical(record, seq, sealedAt)).equals(stored)) {
                    problem = new Problem("RECORD_CHANGED", seq, auditId, "the record no longer matches its seal");
                } else if (record.getCreatedAt() != null && Duration.between(
                        Instant.EPOCH.plus(Duration.ofNanos(sealedAt * 1_000)), record.getCreatedAt()).abs()
                        .compareTo(SEAL_TOLERANCE) > 0) {
                    problem = new Problem("TIME_CHANGED", seq, auditId, "createdAt " + record.getCreatedAt()
                            + " is far from the seal time");
                }
                if (problem != null) {
                    problemCount++;
                    if (problems.size() < MAX_PROBLEMS) {
                        problems.add(problem);
                    }
                }
                if (record != null) {
                    linkedRecords++;
                    if (record.getCreatedAt() != null && (chainStart == null || record.getCreatedAt().isBefore(chainStart))) {
                        chainStart = record.getCreatedAt();
                    }
                }
                // Дальше — от сохранённого хеша: одна правка даёт одну находку, а не разрыв до конца цепочки.
                previous = stored;
                expected = seq + 1;
                checked++;
            }
        }
        if (expected - 1 != headSeq || !previous.equals(headHash) && headSeq > 0) {
            problemCount++;
            if (problems.size() < MAX_PROBLEMS) {
                problems.add(new Problem("HEAD_MISMATCH", headSeq, null,
                        "the chain ends at seq " + (expected - 1) + ", the head says " + headSeq));
            }
        }

        long notCovered = chainStart == null ? count(null, false) : count(chainStart, false);
        long unsealed = chainStart == null ? 0 : count(chainStart, true) - linkedRecords;
        if (unsealed > 0) {
            problemCount++;
            if (problems.size() < MAX_PROBLEMS) {
                problems.add(new Problem("RECORDS_OUTSIDE_CHAIN", null, null,
                        unsealed + " records after the chain start have no link: written around the application"));
            }
        }
        return new AuditIntegrityReport(problemCount == 0, checked, headSeq, chainStart, notCovered, Math.max(unsealed, 0),
                List.copyOf(problems), problemCount > problems.size(), Instant.now());
    }

    private Map<UUID, AuditLog> recordsOf(List<Object[]> links) {
        List<UUID> ids = links.stream().map(link -> toUuid(link[1])).toList();
        Map<UUID, AuditLog> records = new HashMap<>();
        entityManager.createQuery("SELECT a FROM AuditLog a WHERE a.id IN :ids", AuditLog.class)
                .setParameter("ids", ids)
                .getResultList()
                .forEach(record -> records.put(record.getId(), record));
        return records;
    }

    // since == null — все записи; sinceOrAfter — с начала цепочки (true) или до него (false).
    private long count(Instant since, boolean sinceOrAfter) {
        if (since == null) {
            return entityManager.createQuery("SELECT COUNT(a) FROM AuditLog a", Long.class).getSingleResult();
        }
        String where = sinceOrAfter ? "a.createdAt >= :since" : "a.createdAt < :since";
        return entityManager.createQuery("SELECT COUNT(a) FROM AuditLog a WHERE " + where, Long.class)
                .setParameter("since", since)
                .getSingleResult();
    }

    private static UUID toUuid(Object value) {
        return value instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(value));
    }

    private static String summary(AuditIntegrityReport report) {
        StringBuilder out = new StringBuilder();
        for (Problem problem : report.problems()) {
            if (!out.isEmpty()) {
                out.append("; ");
            }
            out.append(problem.kind());
            if (problem.seq() != null) {
                out.append(" at seq ").append(problem.seq());
            }
            if (problem.auditId() != null) {
                out.append(" (record ").append(problem.auditId()).append(')');
            }
            out.append(": ").append(problem.detail());
        }
        if (report.problemsTruncated()) {
            out.append("; and more");
        }
        return out.toString();
    }
}
