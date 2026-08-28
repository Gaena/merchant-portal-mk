package az.millikart.auth.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import az.millikart.auth.domain.User;
import az.millikart.auth.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

// Замена админу, которого раньше засеивал changeset Liquibase (P0-6). Намеренно юнит-уровень:
// раннер обязан вести себя одинаково в любом контексте, а общая in-memory база остальных
// интеграционных тестов не гарантирует "users пуста". Саму проводку (флаг ConditionalOnProperty)
// покрывает AdminBootstrapIntegrationTest.
class AdminBootstrapRunnerTest {

    private static final String VALID_PASSWORD = "BootstrapAdmin123!";

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private final java.util.List<Object> publishedEvents = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
    }

    private AdminBootstrapRunner runner(String username, String password) {
        // Событие аудита о первом админе (P2-14) публикуется, а не пишется, поэтому юнит-тесту
        // хватает собирающего publisher; саму запись покрывает интеграционный тест, где есть
        // слушатель и база.
        return new AdminBootstrapRunner(userRepository, passwordEncoder,
                event -> publishedEvents.add(event), username, password);
    }

    @Test
    @DisplayName("empty database + valid variables: SYSTEM_ADMIN is created with a hashed password")
    void run_emptyDatabase_createsSystemAdmin() {
        when(userRepository.count()).thenReturn(0L);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        runner("Admin@MilliKart.az", VALID_PASSWORD).run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User created = captor.getValue();

        assertEquals("SYSTEM_ADMIN", created.getRole());
        assertNull(created.getCompanyId(), "the system administrator belongs to no company");
        assertEquals("ACTIVE", created.getStatus());
        // Логин приводит присланный username к нижнему регистру и обрезает — сохранённый должен
        // совпадать уже сейчас.
        assertEquals("admin@millikart.az", created.getUsername());
        assertTrue(passwordEncoder.matches(VALID_PASSWORD, created.getPasswordHash()),
                "the password must be stored as a hash the login flow can verify");
        assertTrue(created.getPasswordHash() != null && !created.getPasswordHash().contains(VALID_PASSWORD),
                "the plaintext password must never be stored");
    }

    @Test
    @DisplayName("non-empty database: nothing is created and existing users are untouched")
    void run_databaseWithUsers_createsNothing() {
        when(userRepository.count()).thenReturn(3L);

        runner("admin@millikart.az", VALID_PASSWORD).run(null);

        verify(userRepository).count();
        verify(userRepository, never()).save(any(User.class));
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("the password the migration used to seed is rejected: the admin obeys the same policy as everyone")
    void run_weakPassword_throwsAndCreatesNothing() {
        when(userRepository.count()).thenReturn(0L);

        // Склеено, чтобы удалённый пароль не вернулся в исходники ищущимся литералом; проверяемое
        // значение — ровно то, что было зашито в удалённом changeset.
        AdminBootstrapRunner runner = runner("admin@millikart.az", "admin" + "123");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> runner.run(null));

        assertTrue(ex.getMessage().contains("BOOTSTRAP_ADMIN_PASSWORD"),
                "message must name the variable to fix: " + ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("variables not set: startup fails instead of inventing a credential")
    void run_missingVariables_throwsAndCreatesNothing() {
        when(userRepository.count()).thenReturn(0L);

        IllegalStateException noUsername = assertThrows(IllegalStateException.class,
                () -> runner("", VALID_PASSWORD).run(null));
        IllegalStateException noPassword = assertThrows(IllegalStateException.class,
                () -> runner("admin@millikart.az", "").run(null));
        IllegalStateException neither = assertThrows(IllegalStateException.class,
                () -> runner(null, null).run(null));

        assertTrue(noUsername.getMessage().contains("BOOTSTRAP_ADMIN_USERNAME"));
        assertTrue(noPassword.getMessage().contains("BOOTSTRAP_ADMIN_PASSWORD"));
        assertTrue(neither.getMessage().contains("BOOTSTRAP_ADMIN_USERNAME"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("username that is not an email is rejected: login would never accept it")
    void run_nonEmailUsername_throwsAndCreatesNothing() {
        when(userRepository.count()).thenReturn(0L);

        AdminBootstrapRunner runner = runner("administrator", VALID_PASSWORD);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> runner.run(null));

        assertTrue(ex.getMessage().contains("email"), "message must explain the requirement: " + ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }
}
