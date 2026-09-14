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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

// Слепок терминалов провайдера: что бы ни ответил шлюз, живые терминалы не должны гаснуть от одного
// сбоя. Выключенный терминал приостанавливает платёжные ссылки под ним, поэтому цена ошибки здесь —
// остановленный приём платежей.
class ProviderTerminalSyncTest {

    private ProviderTerminalSource source;
    private ProviderTerminalRepository repository;
    private ProviderTerminalSyncService service;
    private List<ProviderTerminal> stored;

    @BeforeEach
    void setUp() {
        source = Mockito.mock(ProviderTerminalSource.class);
        repository = Mockito.mock(ProviderTerminalRepository.class);
        stored = new ArrayList<>();
        when(repository.findAll()).thenAnswer(invocation -> new ArrayList<>(stored));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TxpgProperties properties = new TxpgProperties();
        properties.setMissingRunsBeforeDisable(3);
        service = new ProviderTerminalSyncService(source, repository, properties);
    }

    // Недоступный шлюз — это «спросить не удалось», а не «терминалов больше нет».
    @Test
    void aFailedQueryChangesNothing() {
        when(source.fetchActive()).thenThrow(new IllegalStateException("connection refused"));

        ProviderTerminalSyncService.SyncOutcome outcome = service.sync();

        Assertions.assertFalse(outcome.applied());
        Assertions.assertEquals("gateway unavailable", outcome.skippedBecause());
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
        when(source.fetchActive()).thenReturn(List.of(new ProviderTerminalRow("E1120020", "BazarStore", "login-1")));

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
        when(source.fetchActive()).thenReturn(List.of(new ProviderTerminalRow("OTHER", "Other", "login-2")));

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

        when(source.fetchActive()).thenReturn(List.of(new ProviderTerminalRow("OTHER", "Other", "login-2")));
        service.sync();
        Assertions.assertEquals(1, terminal.getMissingRuns());

        when(source.fetchActive()).thenReturn(List.of(new ProviderTerminalRow("E1120020", "BazarStore", "login-1")));
        service.sync();

        Assertions.assertEquals(0, terminal.getMissingRuns());
        Assertions.assertTrue(terminal.isActive());
    }

    // Логин и название принадлежат провайдеру: сменил у себя — сменилось и у нас, иначе терминал
    // однажды перестанет ходить в шлюз.
    @Test
    void theLoginAndTitleAlwaysFollowTheProvider() {
        ProviderTerminal terminal = ProviderTerminal.builder()
                .rid("E1120020").title("Old name").login("old-login").active(true).build();
        stored.add(terminal);
        when(source.fetchActive()).thenReturn(List.of(new ProviderTerminalRow("E1120020", "New name", "new-login")));

        service.sync();

        Assertions.assertEquals("New name", terminal.getTitle());
        Assertions.assertEquals("new-login", terminal.getLogin());
    }

    // Одинаковые строки одного мерчанта — это один терминал (например, две привязки PBY), а не
    // неоднозначность: применяются как одна.
    @Test
    void identicalRowsForOneMerchant_countAsOne() {
        when(source.fetchActive()).thenReturn(List.of(
                new ProviderTerminalRow("E1120020", "BazarStore", "BS00001"),
                new ProviderTerminalRow("E1120020", "BazarStore", "BS00001")));

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
                new ProviderTerminalRow("E1120020", "BazarStore", "BS00001"),
                new ProviderTerminalRow("E1120020", "BazarStore", "BS00009")));

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
                new ProviderTerminalRow("NEW", "New shop", "PBY-1"),
                new ProviderTerminalRow("NEW", "New shop", "PBY-2")));

        ProviderTerminalSyncService.SyncOutcome outcome = service.sync();

        Assertions.assertTrue(outcome.applied());
        Assertions.assertEquals(1, outcome.ambiguous());
        verify(repository, never()).save(any());
    }

    // Строка без идентификатора не с чем сопоставить — она пропускается, а проход продолжается.
    @Test
    void aRowWithoutARidIsIgnoredWithoutFailingTheRun() {
        when(source.fetchActive()).thenReturn(List.of(
                new ProviderTerminalRow(null, "Broken", "login-x"),
                new ProviderTerminalRow("E1120020", "BazarStore", "login-1")));

        ProviderTerminalSyncService.SyncOutcome outcome = service.sync();

        Assertions.assertTrue(outcome.applied());
        Assertions.assertEquals(1, outcome.seen());
    }
}
