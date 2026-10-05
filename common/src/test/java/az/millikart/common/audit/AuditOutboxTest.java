package az.millikart.common.audit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Р-85: внутри HTTP-запроса запись журнала ждёт конца запроса — своя транзакция посреди запроса брала второе
// соединение из пула, и десять одновременных отказов вешали сервис. Верни запись сразу — ответы не изменятся,
// изменится только пул под нагрузкой, поэтому момент записи проверяется здесь, на моке репозитория.
class AuditOutboxTest {

    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final AuditLogService auditLogService =
            new AuditLogService(repository, mock(PlatformTransactionManager.class));
    private final AuditOutboxFilter filter = new AuditOutboxFilter();

    @AfterEach
    void leaveNoTransactionBehind() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void insideARequest_recordsWaitForTheEndOfTheRequest() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (request, response) -> {
            inTransaction(() -> deny("first"));
            inTransaction(() -> auditLogService.recordSuccess(AuditEvent.of(
                    AuditEntity.USER, "u-1", AuditAction.CREATE, "admin@test.com", null, "Created user")));
            verify(repository, never()).save(any());
        });

        verify(repository, times(2)).save(any());
    }

    // Отказ обычно и кончается исключением: запись обязана лечь и тогда.
    @Test
    void aFailedRequest_stillWritesItsRecords() {
        assertThrows(ServletException.class, () -> filter.doFilter(
                new MockHttpServletRequest(), new MockHttpServletResponse(), (request, response) -> {
                    inTransaction(() -> deny("then fail"));
                    throw new ServletException("Access denied");
                }));

        verify(repository, times(1)).save(any());
    }

    // Потерянная запись репортится (AUDIT_WRITE_FAILED) и не отменяет остальные и сам ответ.
    @Test
    void oneLostRecord_doesNotStopTheOthers() throws Exception {
        when(repository.save(any()))
                .thenThrow(new IllegalStateException("database is down"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (request, response) -> {
            inTransaction(() -> deny("lost"));
            inTransaction(() -> deny("kept"));
        });

        verify(repository, times(2)).save(any());
    }

    // Вне запроса (планировщик, прямой вызов) и вне транзакции второго соединения никто не держит — пишем сразу.
    @Test
    void outsideARequestOrATransaction_recordsAreWrittenAtOnce() throws Exception {
        inTransaction(() -> deny("scheduler"));
        verify(repository, times(1)).save(any());

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (request, response) -> {
            deny("no transaction");
            verify(repository, times(2)).save(any());
        });
        verify(repository, times(2)).save(any());
    }

    private void deny(String details) {
        auditLogService.logDenied(AuditEntity.USER, "u-1", AuditAction.UPDATE, "head@test.com", "comp-01", details);
    }

    // Как у сервиса с @Transactional: соединение из пула занято, пока тело не кончилось.
    private static void inTransaction(Runnable body) {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            body.run();
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
