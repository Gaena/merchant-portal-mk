package az.millikart.auth.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.millikart.auth.domain.User;
import az.millikart.auth.dto.LoginRequest;
import az.millikart.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// Сквозная проверка, что флаг bootstrap подключён: при auth.bootstrap.enabled=true на свежей
// схеме сервис поднимается ровно с одним работающим SYSTEM_ADMIN. Своя in-memory база: прочие
// тесты auth делят jdbc:h2:mem:auth на всю жизнь JVM, а этому нужна пустая users на старте
// контекста — раньше, чем её успел бы очистить любой тестовый метод.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-bootstrap;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "auth.bootstrap.enabled=true",
        "BOOTSTRAP_ADMIN_USERNAME=bootstrap.admin@millikart.az",
        "BOOTSTRAP_ADMIN_PASSWORD=BootstrapAdmin123!"
})
@AutoConfigureMockMvc
public class AdminBootstrapIntegrationTest {

    private static final String ADMIN_USERNAME = "bootstrap.admin@millikart.az";
    private static final String ADMIN_PASSWORD = "BootstrapAdmin123!";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("the migration seeds nobody; the bootstrap runner creates the single admin")
    void bootstrap_createsExactlyOneSystemAdmin() {
        List<User> users = userRepository.findAll();

        assertEquals(1, users.size(), "the changeset must not seed users of its own");
        User admin = users.get(0);
        assertEquals(ADMIN_USERNAME, admin.getUsername());
        assertEquals("SYSTEM_ADMIN", admin.getRole());
        assertEquals("ACTIVE", admin.getStatus());
        assertNull(admin.getCompanyId());
        assertTrue(admin.getPasswordHash().startsWith("$2"), "password must be stored as a BCrypt hash");
    }

    @Test
    @DisplayName("the bootstrapped admin can actually log in")
    void bootstrappedAdmin_canLogIn() throws Exception {
        LoginRequest login = new LoginRequest(ADMIN_USERNAME, ADMIN_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SYSTEM_ADMIN"));
    }
}
