package az.millikart.common.audit;

import jakarta.persistence.EntityManager;
import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// Запуск и остановка журналирования (PCI DSS 10.2.1.6) — это запуск и остановка сервиса (Р-136): START — когда
// сервис готов к запросам, STOP — при штатной остановке (SIGTERM, systemd stop). START после START без STOP того
// же экземпляра — падение или kill -9, журнал прерывался: такой START пишется исходом UNRESOLVED. Экземпляр —
// сервис и хост (entityId «pbl@host»), чтобы второй экземпляр на другом хосте не считался падением первого.
@Component
@ConditionalOnProperty(name = "mp.audit.lifecycle.enabled", havingValue = "true", matchIfMissing = true)
public class ServiceLifecycleAudit {

    private static final Logger log = LoggerFactory.getLogger(ServiceLifecycleAudit.class);

    private final AuditLogService auditLogService;
    // Не JdbcTemplate: у ecom свой NamedParameterJdbcTemplate к базе шлюза, а EntityManager — всегда наша база.
    private final EntityManager entityManager;
    private final ApplicationContext context;
    private final String service;
    private final String instance;
    private final AtomicBoolean stopped = new AtomicBoolean();

    public ServiceLifecycleAudit(AuditLogService auditLogService, EntityManager entityManager,
                                 ApplicationContext context, @Value("${spring.application.name}") String service) {
        this.auditLogService = auditLogService;
        this.entityManager = entityManager;
        this.context = context;
        this.service = service;
        this.instance = service + "@" + hostName();
    }

    // Событие дочернего контекста (management-порт) доходит и сюда — пишется только свой.
    @EventListener
    public void started(ApplicationReadyEvent event) {
        if (event.getApplicationContext() != context) {
            return;
        }
        String details = "Service " + service + " started on " + instance + " (pid " + ProcessHandle.current().pid() + ")";
        Instant unfinishedRun = unfinishedPreviousRun();
        if (unfinishedRun != null) {
            log.warn("The previous run of {} started at {} has no stop record: it crashed or was killed", instance, unfinishedRun);
            auditLogService.logUnresolved(AuditEntity.SERVICE, instance, AuditAction.START, null, null,
                    details + "; the previous run started at " + unfinishedRun
                            + " ended without a stop record (crash, kill or power loss): the journal may have a gap");
            return;
        }
        auditLogService.recordSuccess(AuditEvent.of(AuditEntity.SERVICE, instance, AuditAction.START, null, null, details));
    }

    @EventListener
    public void stopping(ContextClosedEvent event) {
        if (event.getApplicationContext() != context || !stopped.compareAndSet(false, true)) {
            return;
        }
        try {
            auditLogService.recordSuccess(AuditEvent.of(AuditEntity.SERVICE, instance, AuditAction.STOP, null, null,
                    "Service " + service + " stopping on " + instance + " (pid " + ProcessHandle.current().pid() + ")"));
        } catch (RuntimeException e) {
            // Остановку журнал не задерживает: следующий START пометит этот запуск как незавершённый.
            log.error("{}: stop record lost for {}: {}", AuditLogService.AUDIT_WRITE_FAILED_MARKER, instance, e.getMessage());
        }
    }

    // Момент последнего START, если после него не было STOP; null — прошлый запуск закончился штатно или его не было.
    private Instant unfinishedPreviousRun() {
        try {
            List<Object[]> last = entityManager.createQuery(
                            "SELECT a.action, a.createdAt FROM AuditLog a WHERE a.entityType = :type AND a.entityId = :id "
                                    + "AND a.action IN (:start, :stop) ORDER BY a.createdAt DESC", Object[].class)
                    .setParameter("type", AuditEntity.SERVICE)
                    .setParameter("id", instance)
                    .setParameter("start", AuditAction.START)
                    .setParameter("stop", AuditAction.STOP)
                    .setMaxResults(1)
                    .getResultList();
            if (!last.isEmpty() && AuditAction.START.equals(last.getFirst()[0])) {
                return (Instant) last.getFirst()[1];
            }
        } catch (RuntimeException e) {
            log.warn("Could not read the previous lifecycle record of {}: {}", instance, e.getMessage());
        }
        return null;
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-host";
        }
    }
}
