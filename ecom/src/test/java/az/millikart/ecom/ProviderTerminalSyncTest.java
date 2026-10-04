package az.millikart.ecom;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import az.millikart.ecom.config.TxpgProperties;
import az.millikart.ecom.domain.ProviderTerminal;
import az.millikart.ecom.repository.ProviderTerminalRepository;
import az.millikart.ecom.service.ProviderTerminalSource;
import az.millikart.ecom.service.ProviderTerminalSource.ProviderTerminalRow;
import az.millikart.ecom.service.ProviderTerminalSyncService;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.PlatformTransactionManager;

// Слепок терминалов провайдера: что бы ни ответил шлюз, живые терминалы не должны гаснуть от одного
// сбоя. Выключенный терминал приостанавливает платёжные ссылки под ним, поэтому цена ошибки здесь —
// остановленный приём платежей.
class ProviderTerminalSyncTest {

    private ProviderTerminalSource source;
    private ProviderTerminalRepository repository;
    private PlatformTransactionManager transactionManager;
    private ProviderTerminalSyncService service;
    private List<ProviderTerminal> stored;
    private final Logger serviceLogger = (Logger) LoggerFactory.getLogger(ProviderTerminalSyncService.class);
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();

    @BeforeEach
    void setUp() {
        source = Mockito.mock(ProviderTerminalSource.class);
        repository = Mockito.mock(ProviderTerminalRepository.class);
        stored = new ArrayList<>();
        when(repository.findAll()).thenAnswer(invocation -> new ArrayList<>(stored));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TxpgProperties properties = new TxpgProperties();
        properties.setMissingRunsBeforeDisable(3);
        transactionManager = Mockito.mock(PlatformTransactionManager.class);
        service = new ProviderTerminalSyncService(source, repository, properties, transactionManager);
        events.start();
        serviceLogger.addAppender(events);
    }

    @AfterEach
    void releaseLogs() {
        serviceLogger.detachAppender(events);
    }

    // Недоступный шлюз — это «спросить не удалось», а не «терминалов больше нет».
    @Test
    void aFailedQueryChangesNothing() {
        when(source.fetchActive()).thenThrow(new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection",
                new SQLException("IO Error: The Network Adapter could not establish the connection")));

        ProviderTerminalSyncService.SyncOutcome outcome = service.sync();

        Assertions.assertFalse(outcome.applied());
        Assertions.assertEquals("gateway unavailable: IO Error: The Network Adapter could not establish the connection",
                outcome.skippedBecause());
        verify(repository, never()).save(any());
    }

    // Пустой список технически валиден, но у работающего эквайринга его не бывает: это признак
    // оборванной выборки, и действовать по нему значит выключить всё разом.
    @Test
    void anEmptyAnswerIsRefusedRatherThanApplied() {
        when(source.fetchActive()).thenReturn(List.of());

        ProviderTerminalSyncService.SyncOutcome outcome = service.sync();

        Assertions.assertFalse(outcome.applied());
        Assertions.assertEquals("empty response", outcome.skippedBecause());
        verify(repository, never()).save(any());
    }

    @Test
    void aNewTerminalIsRecordedAsActive() {
        when(source.fetchActive()).thenReturn(List.of(new ProviderTerminalRow("E1120020", "BazarStore", "login-1", "login-1")));

        ProviderTerminalSyncService.SyncOutcome outcome = service.sync();

        Assertions.assertTrue(outcome.applied());
        Assertions.assertEquals(1, outcome.seen());
        Assertions.assertEquals(0, outcome.disabled());
    }

    // Главное правило: одно пропадание ничего не выключает, нужно три подряд.
    @Test
    void aTerminalIsDisabledOnlyAfterThreeConsecutiveAbsences() {
        stored.add(ProviderTerminal.builder().rid("E1120020").active(true).missingRuns(0).build());
        // В выгрузке приходит другой терминал: список не пуст, но нашего в нём нет.
        when(source.fetchActive()).thenReturn(List.of(new ProviderTerminalRow("OTHER", "Other", "login-2", "login-2")));

        Assertions.assertEquals(0, service.sync().disabled(), "first absence must not disable anything");
        Assertions.assertEquals(1, stored.getFirst().getMissingRuns());
        Assertions.assertTrue(stored.getFirst().isActive());

        Assertions.assertEquals(0, service.sync().disabled(), "second absence must not disable anything");
        Assertions.assertTrue(stored.getFirst().isActive());

        Assertions.assertEquals(1, service.sync().disabled(), "the third absence disables it");
        Assertions.assertFalse(stored.getFirst().isActive());
    }

    // Вернулся на втором проходе — счётчик обнуляется, и до гашения дело не доходит.
    @Test
    void aTerminalThatComesBackResetsItsAbsenceCount() {
        ProviderTerminal terminal = ProviderTerminal.builder()
                .rid("E1120020").active(true).missingRuns(0).build();
        stored.add(terminal);

        when(source.fetchActive()).thenReturn(List.of(new ProviderTerminalRow("OTHER", "Other", "login-2", "login-2")));
        service.sync();
        Assertions.assertEquals(1, terminal.getMissingRuns());

        when(source.fetchActive()).thenReturn(List.of(new ProviderTerminalRow("E1120020", "BazarStore", "login-1", "login-1")));
        service.sync();

        Assertions.assertEquals(0, terminal.getMissingRuns());
        Assertions.assertTrue(terminal.isActive());
    }

    // Выключенный после трёх пропаданий терминал вернулся в выгрузку — снова активен: иначе включённый у
    // провайдера терминал навсегда остался бы выключенным у нас, и включить его вручную нельзя (Р-66).
    @Test
    void aDisabledTerminalThatComesBack_isActiveAgain() {
        ProviderTerminal terminal = ProviderTerminal.builder()
                .rid("E1120020").active(false).missingRuns(3).build();
        stored.add(terminal);
        when(source.fetchActive()).thenReturn(List.of(new ProviderTerminalRow("E1120020", "BazarStore", "login-1", "login-1")));

        service.sync();

        Assertions.assertTrue(terminal.isActive());
        Assertions.assertEquals(0, terminal.getMissingRuns());
    }

    // Логин, название и номер терминала принадлежат провайдеру: сменил у себя — сменилось и у нас, иначе
    // заказ однажды уйдёт на чужой или несуществующий терминал (Р-96).
    @Test
    void theLoginAndTitleAlwaysFollowTheProvider() {
        ProviderTerminal terminal = ProviderTerminal.builder()
                .rid("E1120020").title("Old name").login("old-login").terminalRid("OLD-TID").active(true).build();
        stored.add(terminal);
        when(source.fetchActive()).thenReturn(List.of(new ProviderTerminalRow("E1120020", "New name", "new-login", "00044556")));

        service.sync();

        Assertions.assertEquals("New name", terminal.getTitle());
        Assertions.assertEquals("new-login", terminal.getLogin());
        Assertions.assertEquals("00044556", terminal.getTerminalRid());
    }

    // Одинаковые строки одного мерчанта — это один терминал (например, две привязки PBY), а не
    // неоднозначность: применяются как одна.
    @Test
    void identicalRowsForOneMerchant_countAsOne() {
        when(source.fetchActive()).thenReturn(List.of(
                new ProviderTerminalRow("E1120020", "BazarStore", "BS00001", "BS00001"),
                new ProviderTerminalRow("E1120020", "BazarStore", "BS00001", "BS00001")));

        ProviderTerminalSyncService.SyncOutcome outcome = service.sync();

        Assertions.assertEquals(1, outcome.seen());
        Assertions.assertEquals(0, outcome.ambiguous());
        ArgumentCaptor<ProviderTerminal> saved = ArgumentCaptor.forClass(ProviderTerminal.class);
        verify(repository).save(saved.capture());
        Assertions.assertEquals("BS00001", saved.getValue().getLogin());
    }

    // Р-79: у мерчанта одна строка. Пришли два разных логина — какой из них наш, неизвестно: логин
    // не меняется, но мерчант у провайдера есть, и гасить наш терминал из-за этого нельзя.
    @Test
    void differentLoginsForOneMerchant_areNotAppliedAndNeverDisableIt() {
        ProviderTerminal terminal = ProviderTerminal.builder()
                .rid("E1120020").title("BazarStore").login("BS00001").active(true).missingRuns(2).build();
        stored.add(terminal);
        when(source.fetchActive()).thenReturn(List.of(
                new ProviderTerminalRow("E1120020", "BazarStore", "BS00001", "BS00001"),
                new ProviderTerminalRow("E1120020", "BazarStore", "BS00009", "BS00009")));

        for (int run = 0; run < 3; run++) {
            ProviderTerminalSyncService.SyncOutcome outcome = service.sync();
            Assertions.assertEquals(1, outcome.ambiguous());
            Assertions.assertEquals(0, outcome.disabled());
        }

        Assertions.assertEquals("BS00001", terminal.getLogin());
        Assertions.assertEquals(0, terminal.getMissingRuns());
        Assertions.assertTrue(terminal.isActive());
    }

    // Нового мерчанта с двумя логинами не заводим: админ выбрал бы его и получил чужой логин.
    @Test
    void anAmbiguousNewMerchant_isNotCreated() {
        when(source.fetchActive()).thenReturn(List.of(
                new ProviderTerminalRow("NEW", "New shop", "PBY-1", "PBY-1"),
                new ProviderTerminalRow("NEW", "New shop", "PBY-2", "PBY-2")));

        ProviderTerminalSyncService.SyncOutcome outcome = service.sync();

        Assertions.assertTrue(outcome.applied());
        Assertions.assertEquals(1, outcome.ambiguous());
        verify(repository, never()).save(any());
    }

    // Строка без идентификатора не с чем сопоставить — она пропускается, а проход продолжается.
    @Test
    void aRowWithoutARidIsIgnoredWithoutFailingTheRun() {
        when(source.fetchActive()).thenReturn(List.of(
                new ProviderTerminalRow(null, "Broken", "login-x", "login-x"),
                new ProviderTerminalRow("E1120020", "BazarStore", "login-1", "login-1")));

        ProviderTerminalSyncService.SyncOutcome outcome = service.sync();

        Assertions.assertTrue(outcome.applied());
        Assertions.assertEquals(1, outcome.seen());
    }

    // Второй проход идёт к шлюзу только после коммита первого: без замка оба видели новый терминал
    // неизвестным, и второй падал на первичном ключе (ECOM-SYNC-RACE, Р-119).
    @Test
    void aSecondSync_waitsUntilTheFirstHasCommitted() throws Exception {
        ConcurrentSyncs syncs = new ConcurrentSyncs();
        when(source.fetchActive()).thenAnswer(invocation -> {
            syncs.enterTheGateway();
            return List.of(new ProviderTerminalRow("E1120020", "BazarStore", "login-1", "login-1"));
        });

        List<ProviderTerminalSyncService.SyncOutcome> outcomes = syncs.run(service::sync);

        Assertions.assertTrue(outcomes.stream().allMatch(ProviderTerminalSyncService.SyncOutcome::applied));
        InOrder order = Mockito.inOrder(source, transactionManager);
        order.verify(source).fetchActive();
        order.verify(transactionManager).commit(any());
        order.verify(source).fetchActive();
        order.verify(transactionManager).commit(any());
    }

    // Шлюз лёг на ночь: ERROR со стектрейсом — при начале и при смене причины, а не каждые 15 минут, и строка,
    // когда он вернулся (ECOM-SYNC-LOG, Р-98). Текст ошибки Oracle у каждой попытки свой — по нему не сравнивать.
    @Test
    void aGatewayOutage_isLoggedWhenItStartsWhenItChangesAndWhenItEnds() {
        when(source.fetchActive())
                .thenThrow(gatewayUnavailable("a1"))
                .thenThrow(gatewayUnavailable("b2"))
                .thenThrow(gatewayUnavailable("c3"))
                .thenReturn(List.of())
                .thenReturn(List.of())
                .thenReturn(List.of(new ProviderTerminalRow("E1120020", "BazarStore", "login-1", "login-1")));

        for (int run = 0; run < 6; run++) {
            service.sync();
        }

        List<ILoggingEvent> errors = logged(Level.ERROR);
        Assertions.assertEquals(2, errors.size(), errors.toString());
        Assertions.assertNotNull(errors.get(0).getThrowableProxy(), "the stack trace of the outage is printed once");
        Assertions.assertTrue(errors.get(1).getFormattedMessage().contains("no terminals at all"));
        Assertions.assertEquals(1, logged(Level.INFO).stream()
                .filter(event -> event.getFormattedMessage().contains("applied again")).count());
    }

    // Мерчант с двумя логинами приходит так каждый проход: WARN при появлении, строка — когда он снова один.
    @Test
    void anAmbiguousMerchant_isLoggedWhenItAppearsAndWhenItResolves() {
        ProviderTerminalRow ours = new ProviderTerminalRow("E1120020", "BazarStore", "BS00001", "BS00001");
        when(source.fetchActive())
                .thenReturn(List.of(ours, new ProviderTerminalRow("E1120020", "BazarStore", "BS00009", "BS00009")))
                .thenReturn(List.of(ours, new ProviderTerminalRow("E1120020", "BazarStore", "BS00009", "BS00009")))
                .thenReturn(List.of(ours, new ProviderTerminalRow("E1120020", "BazarStore", "BS00009", "BS00009")))
                .thenReturn(List.of(ours));

        for (int run = 0; run < 4; run++) {
            service.sync();
        }

        Assertions.assertEquals(1, logged(Level.WARN).size(), logged(Level.WARN).toString());
        Assertions.assertEquals(1, logged(Level.INFO).stream()
                .filter(event -> event.getFormattedMessage().contains("no longer returns different rows for merchant E1120020"))
                .count());
    }

    private static CannotGetJdbcConnectionException gatewayUnavailable(String connectionId) {
        return new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection", new SQLException(
                "IO Error: The Network Adapter could not establish the connection (CONNECTION_ID=" + connectionId + ")"));
    }

    private List<ILoggingEvent> logged(Level level) {
        return events.list.stream().filter(event -> event.getLevel() == level).toList();
    }
}
