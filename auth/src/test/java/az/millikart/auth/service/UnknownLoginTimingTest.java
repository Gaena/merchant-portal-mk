package az.millikart.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import az.millikart.auth.dto.LoginRequest;
import az.millikart.auth.repository.UserRepository;
import az.millikart.auth.security.LoginRateLimiter;
import az.millikart.common.audit.AuditLogService;
import az.millikart.common.exception.BusinessException;
import az.millikart.common.security.JwtProvider;
import az.millikart.common.security.SecurityConfig;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

// Неизвестный логин обязан тратить на проверку пароля столько же, сколько известный, иначе аккаунты
// перечисляются по часам. Одинаковость ответа сторожит AuthIntegrationTest; здесь — что сравнение с
// заглушкой действительно считает BCrypt. Кривой хэш BCryptPasswordEncoder отвергает сразу, без хэширования,
// а хэш другой стоимости считается быстрее или медленнее — и то и другое снаружи не видно, только по часам.
class UnknownLoginTimingTest {

    // Тот же шаблон, которым BCryptPasswordEncoder решает, считать ли хэш вообще.
    private static final Pattern BCRYPT = Pattern.compile("\\A\\$2(a|y|b)?\\$(\\d\\d)\\$[./0-9A-Za-z]{53}");

    // Заглушку считает тот же кодировщик при старте: при любой стоимости (4 в тестах, 10 в проде) она та же, что
    // у настоящих хэшей.
    @ParameterizedTest
    @ValueSource(ints = {4, 10})
    void anUnknownUsername_isComparedWithAFullStrengthHash(int strength) {
        // Кодировщик — ровно тот, что объявляет приложение.
        PasswordEncoder encoder = spy(new SecurityConfig(null, null, null, false).passwordEncoder(strength));
        UserRepository users = mock(UserRepository.class);
        when(users.findForLoginByUsername("nobody@millikart.az")).thenReturn(Optional.empty());
        AuthService authService = new AuthService(users, encoder, mock(JwtProvider.class),
                mock(RefreshTokenService.class), mock(LoginRateLimiter.class), mock(AuditLogService.class),
                mock(ApplicationEventPublisher.class), mock(PasswordHistoryService.class));

        assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("nobody@millikart.az", "WrongPass123!"), "203.0.113.1"));

        ArgumentCaptor<String> compared = ArgumentCaptor.forClass(String.class);
        verify(encoder, times(1)).matches(eq("WrongPass123!"), compared.capture());
        String placeholder = compared.getValue();
        String real = encoder.encode("SomePassword123!");
        assertTrue(BCRYPT.matcher(placeholder).matches(), "not a BCrypt hash, so nothing is computed: " + placeholder);
        assertEquals(real.substring(0, 7), placeholder.substring(0, 7),
                "the placeholder must use the same BCrypt version and cost as real password hashes");
    }
}
