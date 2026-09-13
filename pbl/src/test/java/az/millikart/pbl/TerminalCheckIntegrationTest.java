package az.millikart.pbl;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.millikart.common.security.JwtProvider;
import az.millikart.pbl.domain.Terminal;
import az.millikart.pbl.provider.AcquiringClient;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Кнопка «Тест» у терминала: кто вправе нажимать и что уходит провайдеру.
 *
 * Пробный заказ — внешний след у провайдера, поэтому проверка закрыта той же ролью, что пароль
 * терминала, и для уже заведённого терминала учётные данные берутся из базы, не выходя наружу.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TerminalCheckIntegrationTest {

    private static final int TERMINAL_ID = 700100;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private TerminalRepository terminalRepository;

    @MockBean
    private AcquiringClient acquiringClient;

    private String adminToken;
    private String headToken;

    @BeforeEach
    void setUp() {
        terminalRepository.deleteAll();
        terminalRepository.save(Terminal.builder()
                .id(TERMINAL_ID)
                .name("Checked terminal")
                .login("stored-login")
                .password("stored-password")
                .companyId("comp-01")
                .build());

        adminToken = "Bearer " + jwtProvider.generateToken("000", "admin@millikart.az", "SYSTEM_ADMIN", null);
        headToken = "Bearer " + jwtProvider.generateToken("111", "head@comp1.com", "COMPANY_HEAD", "comp-01");
    }

    // Уже заведённый терминал проверяется ключом из базы: админ пароль не вводит и не видит.
    @Test
    void existingTerminal_isCheckedWithItsStoredCredentials() throws Exception {
        when(acquiringClient.checkTerminalCredentials("stored-login", "stored-password"))
                .thenReturn(TerminalCheckResult.ok());

        mockMvc.perform(post("/api/v1/acquiring/terminal-checks/{id}", TERMINAL_ID)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome", Matchers.is("OK")))
                // В ответе нет ни логина, ни пароля — только исход.
                .andExpect(jsonPath("$.password").doesNotExist());

        verify(acquiringClient).checkTerminalCredentials("stored-login", "stored-password");
    }

    // Неверный пароль — это результат проверки, а не сбой запроса: 200 и понятный исход.
    @Test
    void wrongPassword_comesBackAsAnOutcomeNotAnError() throws Exception {
        when(acquiringClient.checkTerminalCredentials("new-login", "wrong"))
                .thenReturn(new TerminalCheckResult(TerminalCheckResult.Outcome.INVALID_CREDENTIALS,
                        "InvalidLogin", "Invalid login or password"));

        mockMvc.perform(post("/api/v1/acquiring/terminal-checks")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"new-login\",\"password\":\"wrong\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome", Matchers.is("INVALID_CREDENTIALS")))
                .andExpect(jsonPath("$.message", Matchers.is("Invalid login or password")));
    }

    // Глава компании может менять имя терминала, но не перебирать его ключи — и провайдеру при
    // такой попытке не уходит ни одного пробного заказа.
    @Test
    void anyoneButASystemAdmin_isRefusedAndNothingReachesTheProvider() throws Exception {
        mockMvc.perform(post("/api/v1/acquiring/terminal-checks/{id}", TERMINAL_ID)
                        .header(HttpHeaders.AUTHORIZATION, headToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/acquiring/terminal-checks")
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"x\",\"password\":\"y\"}"))
                .andExpect(status().isForbidden());

        verify(acquiringClient, never()).checkTerminalCredentials(anyString(), anyString());
    }

    @Test
    void unknownTerminal_isNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/acquiring/terminal-checks/{id}", 999999)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound());

        verify(acquiringClient, never()).checkTerminalCredentials(anyString(), eq("stored-password"));
    }
}
