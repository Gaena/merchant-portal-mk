package az.millikart.common.security;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.mock.env.MockEnvironment;

// Сервис без обязательной переменной падает с инструкцией, а не со стектрейсом (P0-5, P1-10). Сбой строится
// настоящим сообщением Spring о нерезолвнутом плейсхолдере: сменится его текст с обновлением Spring —
// анализатор перестанет узнавать переменную, и это покажет тест, а не первый запуск в проде.
class MissingSecretFailureAnalyzerTest {

    private final MissingSecretFailureAnalyzer analyzer = new MissingSecretFailureAnalyzer();

    @ParameterizedTest
    @ValueSource(strings = {"JWT_SECRET", "DB_PASSWORD", "PBL_API_TOKEN", "PBL_BASE_URL",
            "PBL_PROVIDER_GATEWAY_BASE_URL", "PBL_PROVIDER_API_BASE_URL", "CREDENTIALS_ENCRYPTION_KEY"})
    void aMissingVariable_isNamedWithTheCommandThatSetsIt(String variable) {
        FailureAnalysis analysis = analyzer.analyze(unresolved(variable));

        assertNotNull(analysis, variable + " was not recognised");
        assertTrue(analysis.getDescription().contains("The environment variable " + variable + " is not set"),
                analysis.getDescription());
        assertTrue(analysis.getAction().contains("export " + variable + "="), analysis.getAction());
    }

    // У адреса своя причина не иметь дефолта: иначе прод молча шлёт плательщиков на localhost.
    @Test
    void addressesAndSecrets_areExplainedDifferently() {
        assertTrue(analyzer.analyze(unresolved("PBL_BASE_URL")).getDescription().contains("an address with a default"));
        assertTrue(analyzer.analyze(unresolved("JWT_SECRET")).getDescription().contains("a secret with a default"));
    }

    // Имя сверяется в кавычках целиком: PBL_API_TOKEN_ENABLED — не PBL_API_TOKEN, и чужой сбой остаётся
    // стандартному отчёту Spring.
    @ParameterizedTest
    @ValueSource(strings = {"PBL_API_TOKEN_ENABLED", "SOME_OTHER_VARIABLE"})
    void anotherPlaceholder_isLeftToSpring(String variable) {
        assertNull(analyzer.analyze(unresolved(variable)));
    }

    // Биндер @ConfigurationProperties плейсхолдер терпит: пароль «${DB_PASSWORD}» уходит в драйвер текстом,
    // и сбой приходит от базы. Узнаётся по окружению — и когда оно отдаёт литерал, и когда само отказывается.
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aDatabaseLoginFailure_withTheLiteralPlaceholder_isExplained(boolean environmentReturnsTheLiteral) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.password", "${DB_PASSWORD}");
        environment.setIgnoreUnresolvableNestedPlaceholders(environmentReturnsTheLiteral);
        analyzer.setEnvironment(environment);

        FailureAnalysis analysis = analyzer.analyze(new IllegalStateException("password authentication failed for user \"postgres\""));

        assertNotNull(analysis);
        assertTrue(analysis.getDescription().contains("literal text \"${DB_PASSWORD}\""), analysis.getDescription());
        assertTrue(analysis.getAction().contains("export DB_PASSWORD="), analysis.getAction());
    }

    @Test
    void aDatabaseLoginFailure_withARealPassword_isLeftToSpring() {
        analyzer.setEnvironment(new MockEnvironment().withProperty("spring.datasource.password", "not-a-placeholder"));

        assertNull(analyzer.analyze(new IllegalStateException("password authentication failed for user \"postgres\"")));
    }

    // Как на старте: разрешение @Value бросает IllegalArgumentException, завёрнутое в создание бина. MockEnvironment
    // не видит переменных машины, поэтому плейсхолдер не разрешится, даже если переменная экспортирована.
    private static Throwable unresolved(String variable) {
        try {
            new MockEnvironment().resolveRequiredPlaceholders("${" + variable + "}");
        } catch (IllegalArgumentException e) {
            return new BeanCreationException("someBean", "Could not create bean", e);
        }
        throw new AssertionError("${" + variable + "} resolved, the test needs it unresolved");
    }
}
