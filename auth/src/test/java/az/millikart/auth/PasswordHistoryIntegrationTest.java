package az.millikart.auth;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.millikart.auth.domain.Company;
import az.millikart.auth.domain.User;
import az.millikart.auth.dto.ChangePasswordRequest;
import az.millikart.auth.dto.LoginRequest;
import az.millikart.auth.dto.UpdateUserRequest;
import az.millikart.auth.repository.CompanyRepository;
import az.millikart.auth.repository.PasswordHistoryRepository;
import az.millikart.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// PCI DSS 8.3.7 (Р-102): свой новый пароль не повторяет ни один из четырёх последних — текущий и три
// прежних. Пятый назад уже можно. Сброс чужого пароля историю не проверяет: отказ был бы оракулом прежних
// паролей пользователя для администратора.
@SpringBootTest
@AutoConfigureMockMvc
class PasswordHistoryIntegrationTest {

    // Своя сеть (TEST-NET-1): 198.51.100.x и 203.0.113.x заняты тестами, которые нарочно исчерпывают лимит.
    private static final String CLIENT_ADDRESS = "192.0.2.102";
    private static final String RECENT = "The new password must differ from the last 4 passwords";
    private static final String ADMIN = "admin@millikart.az";
    private static final String CLERK = "clerk@comp01.com";
    private static final String[] PASSWORDS = {
            "FirstPassword1!", "SecondPassword2!", "ThirdPassword3!", "FourthPassword4!", "FifthPassword5!"};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PasswordHistoryRepository passwordHistoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        companyRepository.deleteAll();
        companyRepository.save(Company.builder().id("comp-01").name("MilliKart LLC").status("ACTIVE").build());
        seed(ADMIN, "SYSTEM_ADMIN", null, PASSWORDS[0]);
        seed(CLERK, "COMPANY_EMPLOYEE", "comp-01", PASSWORDS[0]);
    }

    @Test
    void theLastFourPasswords_cannotBeReused_theFifthBackCan() throws Exception {
        for (int i = 1; i <= 3; i++) {
            changePassword(CLERK, PASSWORDS[i - 1], PASSWORDS[i]).andExpect(status().isOk());
        }
        // Текущий — FourthPassword4!, прежние — Third, Second, First: все четыре под запретом.
        for (int i = 0; i <= 3; i++) {
            changePassword(CLERK, PASSWORDS[3], PASSWORDS[i]).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", is(RECENT)));
        }
        changePassword(CLERK, PASSWORDS[3], PASSWORDS[4]).andExpect(status().isOk());
        // Теперь First — пятый назад: снова можно.
        changePassword(CLERK, PASSWORDS[4], PASSWORDS[0]).andExpect(status().isOk());
        assertEquals(3, passwordHistoryRepository.findByUserIdOrderByReplacedAtDesc(user(CLERK).getId()).size(),
                "nothing older than the three before the current one is kept");
    }

    // Свой пароль через правку учётки — та же проверка.
    @Test
    void yourOwnPasswordThroughTheUserApi_isCheckedToo() throws Exception {
        String adminToken = tokenOf(ADMIN, PASSWORDS[0]);
        changeOwnPassword(adminToken, PASSWORDS[1]).andExpect(status().isOk());

        changeOwnPassword(tokenOf(ADMIN, PASSWORDS[1]), PASSWORDS[0]).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is(RECENT)));
    }

    // Сброс чужого пароля истории не спрашивает — но пароль, который выдал администратор, владелец при
    // обязательной смене повторить не сможет, как и свои прежние.
    @Test
    void anAdminReset_isNotCheckedAgainstTheHistory_butTheOwnersNextChangeIs() throws Exception {
        changePassword(CLERK, PASSWORDS[0], PASSWORDS[1]).andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/users/" + user(CLERK).getId())
                        .header(HttpHeaders.AUTHORIZATION, tokenOf(ADMIN, PASSWORDS[0]))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRequest(null, null, PASSWORDS[0], null, null, null))))
                .andExpect(status().isOk());

        changePassword(CLERK, PASSWORDS[0], PASSWORDS[1]).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is(RECENT)));
        changePassword(CLERK, PASSWORDS[0], PASSWORDS[2]).andExpect(status().isOk());
    }

    private void seed(String username, String role, String companyId, String password) {
        userRepository.save(User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .fullName("Fixture")
                .role(role)
                .companyId(companyId)
                .status("ACTIVE")
                .build());
    }

    private User user(String username) {
        return userRepository.findByUsername(username).orElseThrow();
    }

    private String tokenOf(String username, String password) throws Exception {
        String body = mockMvc.perform(fromClient(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(body).get("token").asText();
    }

    private ResultActions changePassword(String username, String current, String next) throws Exception {
        return mockMvc.perform(fromClient(post("/api/v1/auth/change-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangePasswordRequest(username, current, next))));
    }

    private ResultActions changeOwnPassword(String token, String next) throws Exception {
        return mockMvc.perform(patch("/api/v1/users/" + user(ADMIN).getId())
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateUserRequest(null, null, next, null, null, null))));
    }

    private static MockHttpServletRequestBuilder fromClient(MockHttpServletRequestBuilder request) {
        return request.with(servletRequest -> {
            servletRequest.setRemoteAddr(CLIENT_ADDRESS);
            return servletRequest;
        });
    }
}
