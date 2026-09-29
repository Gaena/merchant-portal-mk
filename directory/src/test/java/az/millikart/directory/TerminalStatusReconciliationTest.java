package az.millikart.directory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import az.millikart.common.audit.AuditEvent;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

// Каждое правило сверки — про деньги: лишнее выключение останавливает приём платежей, недостающее
// включение оставляет терминал мёртвым навсегда.
class TerminalStatusReconciliationTest {

    private TerminalRepository terminals;
    private ProviderTerminalStatusRepository snapshot;
    private PaymentLinkStatusRepository links;
    private AuditLogService audit;
    private TerminalStatusReconciliationService service;

    @BeforeEach
    void setUp() {
        terminals = Mockito.mock(TerminalRepository.class);
        snapshot = Mockito.mock(ProviderTerminalStatusRepository.class);
        links = Mockito.mock(PaymentLinkStatusRepository.class);
        audit = Mockito.mock(AuditLogService.class);
        when(terminals.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new TerminalStatusReconciliationService(terminals, snapshot, links, audit);
    }

    private Terminal terminal(TerminalStatus status, TerminalStatusSource source, String merchantRid) {
        return Terminal.builder()
                .id(500001)
                .name("Terminal")
                .login("login")
                .companyId("comp-01")
                .status(status)
                .statusSource(source)
                .merchantRid(merchantRid)
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

    // Логин, название и номер терминала принадлежат провайдеру (Р-67, Р-96): смена у него доходит до нашего
    // терминала, и логин пишется Basic-логином шлюза, с префиксом TerminalSys/.
    @Test
    void aProviderLoginChangeReachesOurTerminal() {
        Terminal ours = terminal(TerminalStatus.ACTIVE, TerminalStatusSource.MANUAL, "E1120020");
        when(snapshot.activityByRid()).thenReturn(Map.of("E1120020", true));
        when(snapshot.rowsByRid()).thenReturn(Map.of("E1120020",
                new ProviderTerminalStatusRepository.ProviderTerminalRow("E1120020", "Shop LLC", "BS00005", true, "00044556")));
        when(terminals.findAll()).thenReturn(List.of(ours));

        service.reconcile();

        Assertions.assertEquals("TerminalSys/BS00005", ours.getLogin());
        Assertions.assertEquals("Shop LLC", ours.getName());
        Assertions.assertEquals("00044556", ours.getTerminalRid());
        Assertions.assertEquals(TerminalStatus.ACTIVE, ours.getStatus());
        verify(terminals).save(ours);
    }

    @Test
    void theGatewayLoginCarriesTheOwnerPrefixExactlyOnce() {
        Assertions.assertEquals("TerminalSys/BS00002",
                new ProviderTerminalStatusRepository.ProviderTerminalRow("r", "t", "BS00002", true, null).gatewayLogin());
        Assertions.assertEquals("TerminalSys/BS00002",
                new ProviderTerminalStatusRepository.ProviderTerminalRow("r", "t", "TerminalSys/BS00002", true, null).gatewayLogin());
        Assertions.assertNull(
                new ProviderTerminalStatusRepository.ProviderTerminalRow("r", "t", " ", true, null).gatewayLogin());
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

    // Массовая правка чужих ссылок без человека обязана остаться в журнале, и от имени system, а не
    // администратора. Ловит пропавшую запись BLOCK/UNBLOCK и подменённого актора.
    @Test
    void blockingAndUnblocking_areRecordedInTheJournalAsSystem() {
        Terminal gone = terminal(TerminalStatus.ACTIVE, TerminalStatusSource.MANUAL, "E1120020");
        when(snapshot.activityByRid()).thenReturn(Map.of("E1120020", false));
        when(terminals.findAll()).thenReturn(List.of(gone));
        when(links.suspendActiveLinks(500001)).thenReturn(3);
        service.reconcile();

        when(snapshot.activityByRid()).thenReturn(Map.of("E1120020", true));
        service.reconcile();

        ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, Mockito.times(2)).recordSuccess(events.capture());
        AuditEvent block = events.getAllValues().get(0);
        Assertions.assertEquals("TERMINAL", block.entityType());
        Assertions.assertEquals("500001", block.entityId());
        Assertions.assertEquals("BLOCK", block.action());
        Assertions.assertEquals("system", block.performedBy());
        Assertions.assertEquals("comp-01", block.companyId());
        Assertions.assertTrue(block.details().contains("suspended 3 links"), block.details());
        AuditEvent unblock = events.getAllValues().get(1);
        Assertions.assertEquals("UNBLOCK", unblock.action());
        Assertions.assertEquals("system", unblock.performedBy());
    }

    // Смена названия, логина или номера у провайдера переносится с записью UPDATE от system (Р-67).
    @Test
    void aProviderIdentityChange_isRecordedInTheJournalAsSystem() {
        Terminal ours = terminal(TerminalStatus.ACTIVE, TerminalStatusSource.MANUAL, "E1120020");
        when(snapshot.activityByRid()).thenReturn(Map.of("E1120020", true));
        when(snapshot.rowsByRid()).thenReturn(Map.of("E1120020",
                new ProviderTerminalStatusRepository.ProviderTerminalRow("E1120020", "Renamed Shop", "BS00005", true, "00044556")));
        when(terminals.findAll()).thenReturn(List.of(ours));

        service.reconcile();

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).recordSuccess(event.capture());
        Assertions.assertEquals("UPDATE", event.getValue().action());
        Assertions.assertEquals("system", event.getValue().performedBy());
        Assertions.assertTrue(event.getValue().details().contains("Renamed Shop"), event.getValue().details());
    }

    // Ручную блокировку провайдер, выключивший терминал, не «перехватывает»: источник остаётся MANUAL.
    // Стань он PROVIDER — сверка сама сняла бы блокировку клиента, как только провайдер вернёт терминал.
    @Test
    void aManuallyBlockedTerminal_staysManual_whenTheProviderDisablesIt() {
        Terminal ours = terminal(TerminalStatus.BLOCKED, TerminalStatusSource.MANUAL, "E1120020");
        when(snapshot.activityByRid()).thenReturn(Map.of("E1120020", false));
        when(terminals.findAll()).thenReturn(List.of(ours));
        service.reconcile();

        Assertions.assertEquals(TerminalStatus.BLOCKED, ours.getStatus());
        Assertions.assertEquals(TerminalStatusSource.MANUAL, ours.getStatusSource());

        when(snapshot.activityByRid()).thenReturn(Map.of("E1120020", true));
        TerminalStatusReconciliationService.ReconcileOutcome outcome = service.reconcile();

        Assertions.assertEquals(0, outcome.unblocked());
        Assertions.assertEquals(TerminalStatus.BLOCKED, ours.getStatus());
        verify(links, never()).suspendActiveLinks(anyInt());
        verify(links, never()).resumeSuspendedLinks(anyInt(), any());
        verify(terminals, never()).save(any());
        verify(audit, never()).recordSuccess(any());
    }
}
