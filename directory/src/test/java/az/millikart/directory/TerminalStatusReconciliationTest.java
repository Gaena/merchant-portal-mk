package az.millikart.directory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import az.millikart.common.audit.AuditLogService;
import az.millikart.directory.domain.Terminal;
import az.millikart.directory.domain.TerminalStatus;
import az.millikart.directory.domain.TerminalStatusSource;
import az.millikart.directory.repository.PaymentLinkStatusRepository;
import az.millikart.directory.repository.ProviderTerminalStatusRepository;
import az.millikart.directory.repository.TerminalRepository;
import az.millikart.directory.service.TerminalStatusReconciliationService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Сверка статусов наших терминалов с тем, что видит провайдер.
 *
 * Выключение терминала приостанавливает платёжные ссылки под ним, поэтому каждое правило здесь
 * про деньги: лишнее выключение останавливает приём платежей, недостающее включение оставляет
 * терминал мёртвым навсегда.
 */
class TerminalStatusReconciliationTest {

    private TerminalRepository terminals;
    private ProviderTerminalStatusRepository snapshot;
    private PaymentLinkStatusRepository links;
    private TerminalStatusReconciliationService service;

    @BeforeEach
    void setUp() {
        terminals = Mockito.mock(TerminalRepository.class);
        snapshot = Mockito.mock(ProviderTerminalStatusRepository.class);
        links = Mockito.mock(PaymentLinkStatusRepository.class);
        AuditLogService audit = Mockito.mock(AuditLogService.class);
        when(terminals.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new TerminalStatusReconciliationService(terminals, snapshot, links, audit);
    }

    private Terminal terminal(TerminalStatus status, TerminalStatusSource source, String providerRid) {
        return Terminal.builder()
                .id(500001)
                .name("Terminal")
                .login("login")
                .password("secret")
                .companyId("comp-01")
                .status(status)
                .statusSource(source)
                .providerRid(providerRid)
                .build();
    }

    // Пустой слепок — это «синхронизация ещё не проходила», а не «у провайдера не осталось
    // терминалов». Действовать по нему значит выключить всё сразу.
    @Test
    void anEmptySnapshotChangesNothing() {
        when(snapshot.activityByRid()).thenReturn(Map.of());

        TerminalStatusReconciliationService.ReconcileOutcome outcome = service.reconcile();

        Assertions.assertEquals(0, outcome.blocked());
        Assertions.assertEquals(0, outcome.unblocked());
        verify(terminals, never()).findAll();
    }

    @Test
    void aLiveTerminalGoneAtTheProviderIsBlockedAndItsLinksSuspended() {
        Terminal ours = terminal(TerminalStatus.ACTIVE, TerminalStatusSource.MANUAL, "E1120020");
        when(snapshot.activityByRid()).thenReturn(Map.of("E1120020", false));
        when(terminals.findAll()).thenReturn(List.of(ours));

        TerminalStatusReconciliationService.ReconcileOutcome outcome = service.reconcile();

        Assertions.assertEquals(1, outcome.blocked());
        Assertions.assertEquals(TerminalStatus.BLOCKED, ours.getStatus());
        Assertions.assertEquals(TerminalStatusSource.PROVIDER, ours.getStatusSource());
        verify(links).suspendActiveLinks(500001);
    }

    // Терминал, который выключила синхронизация, она же и включает: иначе одно временное
    // отключение у провайдера гасило бы его навсегда — человеку включать такой запрещено.
    @Test
    void aTerminalTheSyncBlockedIsBroughtBackWhenTheProviderReturnsIt() {
        Terminal ours = terminal(TerminalStatus.BLOCKED, TerminalStatusSource.PROVIDER, "E1120020");
        when(snapshot.activityByRid()).thenReturn(Map.of("E1120020", true));
        when(terminals.findAll()).thenReturn(List.of(ours));

        TerminalStatusReconciliationService.ReconcileOutcome outcome = service.reconcile();

        Assertions.assertEquals(1, outcome.unblocked());
        Assertions.assertEquals(TerminalStatus.ACTIVE, ours.getStatus());
        verify(links).resumeSuspendedLinks(anyInt(), any());
        verify(links).expireSuspendedLinks(anyInt(), any());
    }

    // Терминал, выключенный человеком, не включает никто: это решение клиента, и то, что
    // у провайдера всё в порядке, его не отменяет.
    @Test
    void aTerminalTheClientBlockedIsNeverTouched() {
        Terminal ours = terminal(TerminalStatus.BLOCKED, TerminalStatusSource.MANUAL, "E1120020");
        when(snapshot.activityByRid()).thenReturn(Map.of("E1120020", true));
        when(terminals.findAll()).thenReturn(List.of(ours));

        TerminalStatusReconciliationService.ReconcileOutcome outcome = service.reconcile();

        Assertions.assertEquals(0, outcome.unblocked());
        Assertions.assertEquals(TerminalStatus.BLOCKED, ours.getStatus());
        Assertions.assertEquals(TerminalStatusSource.MANUAL, ours.getStatusSource());
        verify(links, never()).resumeSuspendedLinks(anyInt(), any());
    }

    // Терминал без привязки к провайдеру заведён до синхронизации: сверка его не касается.
    @Test
    void aTerminalWithoutAProviderLinkIsLeftAlone() {
        Terminal ours = terminal(TerminalStatus.ACTIVE, TerminalStatusSource.MANUAL, null);
        when(snapshot.activityByRid()).thenReturn(Map.of("E1120020", false));
        when(terminals.findAll()).thenReturn(List.of(ours));

        TerminalStatusReconciliationService.ReconcileOutcome outcome = service.reconcile();

        Assertions.assertEquals(0, outcome.blocked());
        Assertions.assertEquals(1, outcome.untouched());
        Assertions.assertEquals(TerminalStatus.ACTIVE, ours.getStatus());
    }

    // Отсутствие строки в слепке — это «не знаем», а не «выключен»: выключение фиксируется
    // явным флагом и только после нескольких пропаданий подряд.
    @Test
    void aRidMissingFromTheSnapshotIsNotTreatedAsDisabled() {
        Terminal ours = terminal(TerminalStatus.ACTIVE, TerminalStatusSource.MANUAL, "UNKNOWN-RID");
        when(snapshot.activityByRid()).thenReturn(Map.of("E1120020", true));
        when(terminals.findAll()).thenReturn(List.of(ours));

        TerminalStatusReconciliationService.ReconcileOutcome outcome = service.reconcile();

        Assertions.assertEquals(0, outcome.blocked());
        Assertions.assertEquals(TerminalStatus.ACTIVE, ours.getStatus());
        verify(links, never()).suspendActiveLinks(anyInt());
    }

    // Совпадающие состояния ничего не двигают: иначе каждый проход переприостанавливал бы ссылки.
    @Test
    void matchingStatusesMoveNothing() {
        Terminal ours = terminal(TerminalStatus.ACTIVE, TerminalStatusSource.MANUAL, "E1120020");
        when(snapshot.activityByRid()).thenReturn(Map.of("E1120020", true));
        when(terminals.findAll()).thenReturn(List.of(ours));

        TerminalStatusReconciliationService.ReconcileOutcome outcome = service.reconcile();

        Assertions.assertEquals(0, outcome.blocked());
        Assertions.assertEquals(0, outcome.unblocked());
        verify(links, never()).suspendActiveLinks(anyInt());
        verify(terminals, never()).save(any());
    }
}
