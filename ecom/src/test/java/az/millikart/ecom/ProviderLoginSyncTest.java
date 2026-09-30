package az.millikart.ecom;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import az.millikart.ecom.domain.ProviderLogin;
import az.millikart.ecom.repository.ProviderLoginRepository;
import az.millikart.ecom.service.ProviderLoginSource;
import az.millikart.ecom.service.ProviderLoginSource.ProviderLoginRow;
import az.millikart.ecom.service.ProviderLoginSyncService;
import java.sql.SQLSyntaxErrorException;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.BadSqlGrammarException;

// Слепок логинов мультимерчантов (Р-94): по нему directory проверяет логин компании при сохранении.
// Сбой или пустой ответ шлюза не должны стереть слепок — иначе ни одну компанию нельзя было бы завести.
class ProviderLoginSyncTest {

    private ProviderLoginSource source;
    private ProviderLoginRepository repository;
    private ProviderLoginSyncService service;

    @BeforeEach
    void setUp() {
        source = Mockito.mock(ProviderLoginSource.class);
        repository = Mockito.mock(ProviderLoginRepository.class);
        service = new ProviderLoginSyncService(source, repository);
    }

    // Запрос дошёл, но таблицы нет (локальная схема без LOGIN2MERCHANT, 24.09.2026): причина — ошибка базы,
    // а не «шлюз недоступен», иначе администратор ищет обрыв связи там, где его нет.
    @Test
    void aFailedQueryKeepsThePreviousSnapshot_andNamesTheDatabaseError() {
        when(source.fetchMultiMerchantLogins()).thenThrow(new BadSqlGrammarException("fetch logins", "select …",
                new SQLSyntaxErrorException("ORA-00942: table or view \"TXPG\".\"LOGIN2MERCHANT\" does not exist\n"
                        + "Help: https://docs.oracle.com/error-help/db/ora-00942/")));

        ProviderLoginSyncService.SyncOutcome outcome = service.sync();

        Assertions.assertFalse(outcome.applied());
        Assertions.assertEquals("gateway query failed: ORA-00942: table or view \"TXPG\".\"LOGIN2MERCHANT\" does not exist",
                outcome.skippedBecause());
        verify(repository, never()).deleteAllInBatch();
    }

    @Test
    void anEmptyAnswerKeepsThePreviousSnapshot() {
        when(source.fetchMultiMerchantLogins()).thenReturn(List.of());

        Assertions.assertFalse(service.sync().applied());
        verify(repository, never()).deleteAllInBatch();
    }

    // Выгрузка BazarStore со стенда 24.09.2026: один логин, четыре мерчанта. Слепок заменяется целиком;
    // логин без связей остаётся строкой с пустым мерчантом — проверка скажет «нет мерчантов», а не «нет логина».
    @Test
    @SuppressWarnings("unchecked")
    void theSnapshotIsReplacedWithEveryLinkAndLoginsWithoutLinks() {
        when(source.fetchMultiMerchantLogins()).thenReturn(List.of(
                new ProviderLoginRow("bazarstore@company.com", "Active", "Active", "223456789054323", "BazarStore PortBaku"),
                new ProviderLoginRow("bazarstore@company.com", "Active", "Active", "223456789054322", "BazarStore Xetai"),
                new ProviderLoginRow("bazarstore@company.com", "Active", "Active", "123456789054321", "BazarStore Genclik"),
                new ProviderLoginRow("bazarstore@company.com", "Active", "Active", "223456789054324", "BazarStore Xirdalan"),
                new ProviderLoginRow("lonely@company.com", "Active", null, null, null),
                new ProviderLoginRow(" ", "Active", "Active", "1", "no login")));

        ProviderLoginSyncService.SyncOutcome outcome = service.sync();

        Assertions.assertEquals(new ProviderLoginSyncService.SyncOutcome(true, 2, 4, null), outcome);
        verify(repository).deleteAllInBatch();
        ArgumentCaptor<List<ProviderLogin>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saved.capture());
        Assertions.assertEquals(5, saved.getValue().size(), "the row without a login is dropped");
        ProviderLogin lonely = saved.getValue().get(4);
        Assertions.assertEquals("lonely@company.com", lonely.getLogin());
        Assertions.assertNull(lonely.getMerchantRid());
        Assertions.assertNotNull(lonely.getSyncedAt());
    }

    // Статусы логина и связи ложатся в слепок как пришли, без фильтра: по ним directory отличает «логин
    // выключен» от «логина нет» (Р-94), а скоуп берёт только активные связи (Р-97).
    @Test
    @SuppressWarnings("unchecked")
    void theStatusesOfTheLoginAndTheLink_areStoredAsSent() {
        when(source.fetchMultiMerchantLogins()).thenReturn(List.of(
                new ProviderLoginRow("frozen@company.com", "Blocked", "Active", "M-1", "Frozen"),
                new ProviderLoginRow("shop@company.com", "Active", "Inactive", "M-2", "Unlinked")));

        service.sync();

        ArgumentCaptor<List<ProviderLogin>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saved.capture());
        Assertions.assertEquals(List.of("frozen@company.com Blocked Active M-1", "shop@company.com Active Inactive M-2"),
                saved.getValue().stream()
                        .map(link -> link.getLogin() + " " + link.getLoginStatus() + " " + link.getLinkStatus()
                                + " " + link.getMerchantRid())
                        .toList());
    }

    @Test
    void rowsWithoutLoginsOnly_keepThePreviousSnapshot() {
        when(source.fetchMultiMerchantLogins()).thenReturn(List.of(new ProviderLoginRow(null, "Active", "Active", "1", "x")));

        Assertions.assertFalse(service.sync().applied());
        verify(repository, never()).saveAll(any());
    }
}
