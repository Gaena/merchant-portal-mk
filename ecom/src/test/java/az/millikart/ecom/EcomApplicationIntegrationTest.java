package az.millikart.ecom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.millikart.common.exception.ConflictException;
import az.millikart.common.security.JwtProvider;
import az.millikart.ecom.domain.ProviderOrderAttempt;
import az.millikart.ecom.service.ProviderLoginSource.ProviderLoginRow;
import az.millikart.ecom.service.ProviderLoginSource;
import az.millikart.ecom.service.ProviderOrderAttemptService;
import az.millikart.ecom.service.ProviderTerminalSource.ProviderTerminalRow;
import az.millikart.ecom.service.ProviderTerminalSource;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.web.servlet.MockMvc;

// ecom целиком, на H2 вместо нашей PostgreSQL и отдельной H2 вместо шлюза. Сам подъём контекста проверяет
// то, чего не видит ни один юнит-тест: маппинг сущностей на схему после миграций (ddl-auto: validate) и
// JPQL репозиториев. Выписка и справочники провайдера здесь не читаются — их SQL только для Oracle шлюза.
@SpringBootTest
@AutoConfigureMockMvc
class EcomApplicationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private DataSource portalDataSource;

    @Autowired
    @Qualifier("txpgDataSource")
    private DataSource txpgDataSource;

    @MockBean
    private ProviderTerminalSource terminalSource;

    @MockBean
    private ProviderLoginSource loginSource;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ProviderOrderAttemptService attempts;

    private JdbcTemplate portal;

    @BeforeEach
    void setUp() {
        portal = new JdbcTemplate(portalDataSource);
        portal.update("DELETE FROM provider_order_attempts");
        portal.update("DELETE FROM provider_logins");
        portal.update("DELETE FROM provider_terminals");
        portal.update("DELETE FROM audit_logs");
        portal.update("DELETE FROM terminals");
        portal.update("DELETE FROM companies");
    }

    // @Primary на нашей базе: без него миграции, JPA и журнал однажды уехали бы в чужую базу шлюза. Пул к шлюзу —
    // только на чтение.
    @Test
    void migrationsAndEntities_liveInThePortalDatabase_neverInTheGateway() {
        assertThat(portal.queryForList("SELECT LOWER(table_name) FROM information_schema.tables "
                + "WHERE table_schema = 'PUBLIC'", String.class))
                .contains("provider_terminals", "provider_logins", "audit_logs", "databasechangelog");

        assertThat(new JdbcTemplate(txpgDataSource).queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'PUBLIC'", Integer.class))
                .isZero();
        assertThat(portalDataSource).isNotSameAs(txpgDataSource);
        assertThat(((HikariDataSource) txpgDataSource).isReadOnly()).isTrue();
    }

    // Скоуп на настоящей базе (Р-97): компания → её логин мультимерчанта → активные связи в слепке. Отвязанный
    // мерчант уходит, заблокированный логин мерчантов чужими не делает; администратор видит мерчантов логинов
    // всех компаний, включая удалённые.
    @Test
    void eachRoleSeesTheMerchantsOfItsScope() throws Exception {
        company("comp-shop", "ACTIVE", "MultiMerchantSys/shop");
        company("comp-other", "ACTIVE", "MultiMerchantSys/other");
        company("comp-gone", "DELETED", "MultiMerchantSys/gone");
        company("comp-plain", "ACTIVE", "TerminalSys/plain");
        link("shop", "Active", "Active", "M-1", "Shop One");
        link("shop", "Active", "Inactive", "M-2", "Shop Two");
        link("other", "Blocked", "Active", "M-3", "Other");
        link("gone", "Active", "Active", "M-9", "Gone");
        link("stranger", "Active", "Active", "M-7", "Not ours");

        mockMvc.perform(terminals(token("COMPANY_HEAD", "comp-shop")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].merchantRid", contains("M-1")))
                .andExpect(jsonPath("$[0].title", is("Shop One")));
        mockMvc.perform(terminals(token("COMPANY_EMPLOYEE", "comp-other")))
                .andExpect(jsonPath("$[*].merchantRid", contains("M-3")));
        mockMvc.perform(terminals(token("COMPANY_MANAGER", "comp-plain")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
        // Порядок — по названию: Gone, Other, Shop One.
        for (String globalReader : List.of("SYSTEM_ADMIN", "AUDITOR")) {
            mockMvc.perform(terminals(token(globalReader, null)))
                    .andExpect(jsonPath("$[*].merchantRid", contains("M-9", "M-3", "M-1")));
        }
    }

    // Роль компании без компании — отказ с записью в журнал: пустой список выглядел бы как «платежей нет».
    @Test
    void aRoleWithoutACompany_isRefusedAndRecorded() throws Exception {
        mockMvc.perform(terminals(token("COMPANY_HEAD", null)))
                .andExpect(status().isForbidden());

        List<Map<String, Object>> records = portal.queryForList("SELECT * FROM audit_logs");
        assertThat(records).hasSize(1);
        assertThat(records.getFirst())
                .containsEntry("ENTITY_TYPE", "TERMINAL")
                .containsEntry("ACTION", "LIST")
                .containsEntry("ENTITY_ID", "ALL")
                .containsEntry("OUTCOME", "DENIED")
                .containsEntry("PERFORMED_BY", "user@test.com")
                .containsEntry("DETAILS", "Denied: COMPANY_HEAD without a company asked for acquiring transactions");
    }

    // Справочник — карта всех мерчантов провайдера, включая чужих, а синхронизация ходит в боевой шлюз:
    // обе — только SYSTEM_ADMIN. Отказ наступает до шлюза и остаётся в журнале, как у directory.
    @Test
    void theProviderDirectoryAndItsSync_areForTheAdministratorOnly() throws Exception {
        List<String> roles = List.of("COMPANY_HEAD", "COMPANY_MANAGER", "COMPANY_EMPLOYEE", "AUDITOR", "HACKER");
        for (String role : roles) {
            String token = token(role, "AUDITOR".equals(role) ? null : "comp-shop");
            mockMvc.perform(get("/api/v1/ecom/provider-terminals").header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/ecom/provider-terminals/sync").header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(terminalSource, loginSource);

        List<Map<String, Object>> records = portal.queryForList("SELECT * FROM audit_logs");
        assertThat(records).hasSize(2 * roles.size()).allSatisfy(record -> assertThat(record)
                .containsEntry("ENTITY_TYPE", "TERMINAL")
                .containsEntry("ENTITY_ID", "ALL")
                .containsEntry("OUTCOME", "DENIED")
                .containsEntry("PERFORMED_BY", "user@test.com"));
        assertThat(records).extracting(record -> record.get("ACTION") + " " + record.get("DETAILS"))
                .contains("LIST Denied: role COMPANY_HEAD attempted to list provider terminals",
                        "UPDATE Denied: role COMPANY_HEAD attempted to sync provider snapshots",
                        "LIST Denied: role HACKER attempted to list provider terminals",
                        "UPDATE Denied: role AUDITOR attempted to sync provider snapshots");
    }

    // Синхронизация по кнопке пишет оба слепка в нашу базу как есть: статусы логина и связи не фильтруются
    // и не переписываются — по ним directory проверяет логин компании (Р-94), а скоуп берёт только активные
    // связи (Р-97). Выключенный терминал, вернувшийся в выгрузку, снова активен (Р-66).
    @Test
    void anAdministratorsSync_storesBothSnapshotsAsTheProviderSentThem() throws Exception {
        portal.update("INSERT INTO provider_terminals (rid, title, login, active, missing_runs) "
                + "VALUES ('M-1', 'Old title', 'TerminalSys/old', FALSE, 3)");
        when(terminalSource.fetchActive()).thenReturn(List.of(
                new ProviderTerminalRow("M-1", "Shop One", "TerminalSys/shop", "TID-1")));
        when(loginSource.fetchMultiMerchantLogins()).thenReturn(List.of(
                new ProviderLoginRow("shop", "Active", "Active", "M-1", "Shop One"),
                new ProviderLoginRow("shop", "Active", "Inactive", "M-2", "Shop Two"),
                new ProviderLoginRow("frozen", "Blocked", "Active", "M-5", "Frozen")));

        mockMvc.perform(post("/api/v1/ecom/provider-terminals/sync")
                        .header(HttpHeaders.AUTHORIZATION, token("SYSTEM_ADMIN", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied", is(true)))
                .andExpect(jsonPath("$.logins.applied", is(true)));

        assertThat(portal.queryForMap("SELECT title, login, terminal_rid, active, missing_runs "
                + "FROM provider_terminals WHERE rid = 'M-1'"))
                .containsEntry("TITLE", "Shop One")
                .containsEntry("LOGIN", "TerminalSys/shop")
                .containsEntry("TERMINAL_RID", "TID-1")
                .containsEntry("ACTIVE", true)
                .containsEntry("MISSING_RUNS", 0);
        assertThat(portal.queryForList("SELECT login || '|' || login_status || '|' || link_status || '|' || merchant_rid "
                + "FROM provider_logins ORDER BY merchant_rid", String.class))
                .containsExactly("shop|Active|Active|M-1", "shop|Active|Inactive|M-2", "frozen|Blocked|Active|M-5");
        mockMvc.perform(get("/api/v1/ecom/provider-terminals")
                        .header(HttpHeaders.AUTHORIZATION, token("SYSTEM_ADMIN", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].rid", contains("M-1")));
    }

    // Контексты тестов живут весь прогон: задача по расписанию сработала бы посреди чужого теста. Новый
    // планировщик без выключателя в тестовом yaml уронит этот тест.
    @Test
    void noTaskRunsByTheClockInTests() {
        // Держатель задач есть всегда (@EnableScheduling): без него проверка прошла бы впустую.
        java.util.Collection<ScheduledTaskHolder> holders = applicationContext.getBeansOfType(ScheduledTaskHolder.class).values();
        org.junit.jupiter.api.Assertions.assertFalse(holders.isEmpty());
        List<String> tasks = holders.stream()
                .flatMap(holder -> holder.getScheduledTasks().stream())
                .map(String::valueOf)
                .toList();
        org.junit.jupiter.api.Assertions.assertTrue(tasks.isEmpty(), "scheduled in tests: " + tasks);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder terminals(String token) {
        return get("/api/v1/ecom/transactions/terminals").header(HttpHeaders.AUTHORIZATION, token);
    }

    // Строка попытки и есть замок заказа выписки (Р-125): вторая попытка обязана упасть на ключе и получить 409,
    // а не тихо перезаписать первую (merge вместо INSERT) и уйти к провайдеру вторым возвратом.
    @Test
    void aSecondAttemptOnTheSameOrder_isRefusedByTheKey() {
        attempts.begin("175900", ProviderOrderAttempt.Kind.REFUND, new BigDecimal("10.00"), "first@test.com");

        assertThatThrownBy(() -> attempts.begin("175900", ProviderOrderAttempt.Kind.REFUND, new BigDecimal("20.00"),
                "second@test.com"))
                .isInstanceOf(ConflictException.class);
        assertThat(portal.queryForObject("SELECT started_by FROM provider_order_attempts WHERE order_id = '175900'",
                String.class)).isEqualTo("first@test.com");
    }

    private String token(String role, String companyId) {
        return "Bearer " + jwtProvider.generateToken("u-1", "user@test.com", role, companyId);
    }

    private void company(String id, String status, String providerLogin) {
        portal.update("INSERT INTO companies (id, name, status, provider_login) VALUES (?, ?, ?, ?)",
                id, id, status, providerLogin);
    }

    private void link(String login, String loginStatus, String linkStatus, String merchantRid, String title) {
        portal.update("INSERT INTO provider_logins (login, login_status, link_status, merchant_rid, merchant_title, synced_at) "
                + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)", login, loginStatus, linkStatus, merchantRid, title);
    }
}
