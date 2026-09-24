package az.millikart.pbl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.millikart.common.security.CredentialCipher;
import az.millikart.common.security.JwtProvider;
import az.millikart.pbl.domain.Terminal;
import az.millikart.pbl.provider.AcquiringClient;
import az.millikart.pbl.provider.ProviderCredentials;
import az.millikart.pbl.provider.dto.TerminalCheckResult;
import az.millikart.pbl.repository.TerminalRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

// Кнопка «Тест» у заведённого терминала: кто вправе нажимать и что уходит провайдеру. С Р-93 проверка
// идёт с кредами компании терминала — у терминала своих больше нет, — и наружу они не выходят.
@SpringBootTest
@AutoConfigureMockMvc
class TerminalCheckIntegrationTest {

    private static final int TERMINAL_ID = 700100;

    private static final ProviderCredentials COMPANY_CREDENTIALS = new ProviderCredentials(
            CompanyCredentialsFixture.loginOf("comp-01"), CompanyCredentialsFixture.passwordOf("comp-01"));

    // Номер терминала у провайдера — «Тест» заводит пробный заказ на нём (Р-96).
    private static final String TERMINAL_RID = "TID-stored-login";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private TerminalRepository terminalRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CredentialCipher credentialCipher;

    @MockBean
    private AcquiringClient acquiringClient;

    private String adminToken;
    private String headToken;

    @BeforeEach
    void setUp() {
        terminalRepository.deleteAll();
        CompanyCredentialsFixture.seed(jdbcTemplate, credentialCipher, "comp-01");
        terminalRepository.save(Terminal.builder()
                .id(TERMINAL_ID)
                .name("Checked terminal")
                .login("stored-login").terminalRid("TID-stored-login")
                .companyId("comp-01")
                .build());

        adminToken = "Bearer " + jwtProvider.generateToken("000", "admin@millikart.az", "SYSTEM_ADMIN", null);
        headToken = "Bearer " + jwtProvider.generateToken("111", "head@comp1.com", "COMPANY_HEAD", "comp-01");
    }

    // Провайдеру уходят расшифрованные креды компании, а не логин терминала; в ответе — только исход.
    @Test
    void existingTerminal_isCheckedWithItsCompanyCredentials() throws Exception {
        when(acquiringClient.checkOrderCreation(COMPANY_CREDENTIALS, TERMINAL_RID)).thenReturn(TerminalCheckResult.ok());

        mockMvc.perform(post("/api/v1/acquiring/terminal-checks/{id}", TERMINAL_ID)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome", Matchers.is("OK")))
                .andExpect(jsonPath("$.password").doesNotExist());

        verify(acquiringClient).checkOrderCreation(COMPANY_CREDENTIALS, TERMINAL_RID);
    }

    // Неверные креды — это результат проверки, а не сбой запроса: 200 и понятный исход.
    @Test
    void wrongPassword_comesBackAsAnOutcomeNotAnError() throws Exception {
        when(acquiringClient.checkOrderCreation(COMPANY_CREDENTIALS, TERMINAL_RID))
                .thenReturn(new TerminalCheckResult(TerminalCheckResult.Outcome.INVALID_CREDENTIALS,
                        "InvalidLogin", "Invalid login or password"));

        mockMvc.perform(post("/api/v1/acquiring/terminal-checks/{id}", TERMINAL_ID)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome", Matchers.is("INVALID_CREDENTIALS")))
                .andExpect(jsonPath("$.message", Matchers.is("Invalid login or password")));
    }

    // Глава компании может менять имя терминала, но не перебирать ключи — и провайдеру при такой
    // попытке не уходит ни одного пробного заказа.
    @Test
    void anyoneButASystemAdmin_isRefusedAndNothingReachesTheProvider() throws Exception {
        mockMvc.perform(post("/api/v1/acquiring/terminal-checks/{id}", TERMINAL_ID)
                        .header(HttpHeaders.AUTHORIZATION, headToken))
                .andExpect(status().isForbidden());

        verify(acquiringClient, never()).checkOrderCreation(any(), any());
    }

    @Test
    void unknownTerminal_isNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/acquiring/terminal-checks/{id}", 999999)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound());

        verify(acquiringClient, never()).checkOrderCreation(any(), any());
    }

    // Компания без кредов — отказ до провайдера (Р-93): иначе он ответил бы InvalidLogin, и администратор
    // искал бы ошибку не там.
    @Test
    void companyWithoutCredentials_isRefusedBeforeTheProvider() throws Exception {
        jdbcTemplate.update("UPDATE companies SET provider_login = NULL, provider_password = NULL WHERE id = 'comp-01'");

        mockMvc.perform(post("/api/v1/acquiring/terminal-checks/{id}", TERMINAL_ID)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", Matchers.containsString("has no acquirer credentials")));

        verify(acquiringClient, never()).checkOrderCreation(any(), any());
    }

    // Терминал без номера у провайдера (заведён до Р-96 без справочника) — отказ до провайдера: заказ без
    // терминала провайдер не примет.
    @Test
    void terminalWithoutProviderNumber_isRefusedBeforeTheProvider() throws Exception {
        jdbcTemplate.update("UPDATE terminals SET terminal_rid = NULL WHERE id = ?", TERMINAL_ID);

        mockMvc.perform(post("/api/v1/acquiring/terminal-checks/{id}", TERMINAL_ID)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", Matchers.containsString("has no provider terminal number")));

        verify(acquiringClient, never()).checkOrderCreation(any(), any());
    }

    // Проверка терминала до заведения снята (Р-93): логина и пароля у терминала больше нет.
    @Test
    void checkOfATerminalBeingCreated_isGone() throws Exception {
        mockMvc.perform(post("/api/v1/acquiring/terminal-checks")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound());

        verify(acquiringClient, never()).checkOrderCreation(any(), any());
    }
}
