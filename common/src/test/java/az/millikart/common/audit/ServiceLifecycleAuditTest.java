package az.millikart.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;

// Р-136: запуск и остановка сервиса — включение и выключение журнала (PCI DSS 10.2.1.6). Ловит START после
// START без STOP, записанный обычным успехом (перерыв журнала не виден), STOP, записанный дважды или по закрытию
// дочернего контекста management-порта, и START по событию чужого контекста.
class ServiceLifecycleAuditTest {

    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
    @SuppressWarnings("unchecked")
    private final TypedQuery<Object[]> lastRecord = mock(TypedQuery.class);
    private ServiceLifecycleAudit audit;

    @BeforeEach
    void setUp() {
        when(entityManager.createQuery(anyString(), eq(Object[].class))).thenReturn(lastRecord);
        when(lastRecord.setParameter(anyString(), any())).thenReturn(lastRecord);
        when(lastRecord.setMaxResults(anyInt())).thenReturn(lastRecord);
        audit = new ServiceLifecycleAudit(auditLogService, entityManager, context, "pbl");
    }

    @Test
    void theFirstStart_isAnOrdinaryRecord() {
        when(lastRecord.getResultList()).thenReturn(List.of());

        audit.started(ready(context));

        AuditEvent event = recorded();
        assertThat(event.entityType()).isEqualTo(AuditEntity.SERVICE);
        assertThat(event.entityId()).startsWith("pbl@");
        assertThat(event.action()).isEqualTo(AuditAction.START);
        assertThat(event.details()).startsWith("Service pbl started on pbl@");
        verify(auditLogService, never()).logUnresolved(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aStartAfterAStop_isAnOrdinaryRecord() {
        when(lastRecord.getResultList()).thenReturn(List.<Object[]>of(new Object[] {AuditAction.STOP, Instant.now()}));

        audit.started(ready(context));

        assertThat(recorded().action()).isEqualTo(AuditAction.START);
    }

    // Прошлый запуск не оставил STOP — сервис упал или его убили: журнал прерывался, и START это называет.
    @Test
    void aStartAfterAStartWithoutAStop_isUnresolved() {
        Instant previous = Instant.parse("2026-10-07T09:00:00Z");
        when(lastRecord.getResultList()).thenReturn(List.<Object[]>of(new Object[] {AuditAction.START, previous}));

        audit.started(ready(context));

        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).logUnresolved(eq(AuditEntity.SERVICE), anyString(), eq(AuditAction.START), isNull(),
                isNull(), details.capture());
        assertThat(details.getValue()).contains("previous run started at " + previous).contains("without a stop record");
        verify(auditLogService, never()).recordSuccess(any());
    }

    @Test
    void stop_isWrittenOnce_andOnlyForItsOwnContext() {
        audit.stopping(new ContextClosedEvent(mock(ConfigurableApplicationContext.class)));
        verify(auditLogService, never()).recordSuccess(any());

        audit.stopping(new ContextClosedEvent(context));
        audit.stopping(new ContextClosedEvent(context));

        verify(auditLogService, times(1)).recordSuccess(any());
        assertThat(recorded().action()).isEqualTo(AuditAction.STOP);
    }

    @Test
    void theReadyEventOfAnotherContext_writesNothing() {
        audit.started(ready(mock(ConfigurableApplicationContext.class)));

        verify(auditLogService, never()).recordSuccess(any());
        verify(auditLogService, never()).logUnresolved(any(), any(), any(), any(), any(), any());
    }

    private AuditEvent recorded() {
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogService).recordSuccess(event.capture());
        return event.getValue();
    }

    private static ApplicationReadyEvent ready(ConfigurableApplicationContext source) {
        return new ApplicationReadyEvent(new SpringApplication(), new String[0], source, Duration.ZERO);
    }
}
