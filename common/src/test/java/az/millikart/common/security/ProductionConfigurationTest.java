package az.millikart.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

// Боевые application.yaml всех сервисов, без Spring. Тесты сервисов идут на тестовых yaml, которые
// заменяют боевые целиком, поэтому боевые настройки безопасности не читает никто: actuator на 0.0.0.0,
// Swagger включённый по умолчанию или секрет с дефолтом прошли бы все прогоны (AGENTS §6, §12 п. 4).
class ProductionConfigurationTest {

    // Тест идёт из каталога модуля common, сервисы — соседние модули.
    private static Map<String, Object> productionYaml(String service) throws IOException {
        Path path = Path.of("..", service, "src", "main", "resources", "application.yaml");
        assertTrue(Files.exists(path), "missing " + path.toAbsolutePath() + " — the check must not pass silently");
        return new Yaml().load(Files.readString(path, StandardCharsets.UTF_8));
    }

    // Actuator отвечает без токена, поэтому он только на петле; иначе пробы и метрики видны снаружи.
    @ParameterizedTest
    @ValueSource(strings = {"auth", "directory", "pbl", "ecom"})
    void actuator_listensOnLoopbackOnly(String service) throws IOException {
        Map<String, Object> yaml = productionYaml(service);

        assertEquals("127.0.0.1", String.valueOf(value(yaml, "management.server.address")), service);
        assertTrue(Pattern.matches("\\$\\{MANAGEMENT_PORT:\\d+}", String.valueOf(value(yaml, "management.server.port"))),
                service + ": the management port must be its own, " + value(yaml, "management.server.port"));
    }

    // /v3/api-docs — полная карта API: по умолчанию выключено, включается только на время приёмки.
    @ParameterizedTest
    @ValueSource(strings = {"auth", "directory", "pbl", "ecom"})
    void swagger_isOffByDefault(String service) throws IOException {
        Map<String, Object> yaml = productionYaml(service);

        assertEquals("${SWAGGER_ENABLED:false}", value(yaml, "springdoc.api-docs.enabled"), service);
        assertEquals("${SWAGGER_ENABLED:false}", value(yaml, "springdoc.swagger-ui.enabled"), service);
    }

    // Статический токен даёт SYSTEM_ADMIN без пароля: выключен и без непустого дефолта (известный дефолт —
    // бэкдор). Где ключа нет, работает умолчание JwtAuthFilter — false.
    @ParameterizedTest
    @ValueSource(strings = {"auth", "directory", "pbl", "ecom"})
    void staticAdminToken_isOffAndHasNoDefault(String service) throws IOException {
        Map<String, Object> yaml = productionYaml(service);

        Object enabled = value(yaml, "pbl.security.api-token-enabled");
        assertTrue(enabled == null || "${PBL_API_TOKEN_ENABLED:false}".equals(enabled), service + ": " + enabled);
        Object token = value(yaml, "pbl.security.api-token");
        assertTrue(token == null || "${PBL_API_TOKEN:}".equals(token) || "${PBL_API_TOKEN}".equals(token),
                service + ": the static token must have no value in the file, got " + token);
    }

    // Секреты — только из окружения, ровно ${VAR}: дефолт у секрета — ключ, опубликованный с кодом (P0-5).
    // Срок жизни токена — 15 минут: в JwtProvider умолчание 24 часа, и правду задаёт только этот файл.
    @ParameterizedTest
    @ValueSource(strings = {"auth", "directory", "pbl", "ecom"})
    void secretsComeFromTheEnvironment_andTokensLiveFifteenMinutes(String service) throws IOException {
        Map<String, Object> yaml = productionYaml(service);

        assertEquals("${JWT_SECRET}", value(yaml, "pbl.security.jwt.secret"), service);
        assertEquals("${DB_PASSWORD}", value(yaml, "spring.datasource.password"), service);
        assertEquals("${JWT_EXPIRATION_MS:900000}", value(yaml, "pbl.security.jwt.expiration-ms"), service);
    }

    // Пароль к базе шлюза провайдера — тоже только из окружения.
    @ParameterizedTest
    @ValueSource(strings = {"ecom"})
    void gatewayCredentialsComeFromTheEnvironment(String service) throws IOException {
        Map<String, Object> yaml = productionYaml(service);

        assertEquals("${ECOM_TXPG_URL}", value(yaml, "ecom.txpg.datasource.url"), service);
        assertEquals("${ECOM_TXPG_USERNAME}", value(yaml, "ecom.txpg.datasource.username"), service);
        assertEquals("${ECOM_TXPG_PASSWORD}", value(yaml, "ecom.txpg.datasource.password"), service);
    }

    @SuppressWarnings("unchecked")
    private static Object value(Map<String, Object> yaml, String dottedKey) {
        Object node = yaml;
        for (String key : dottedKey.split("\\.")) {
            if (!(node instanceof Map<?, ?> map)) {
                return null;
            }
            node = ((Map<String, Object>) map).get(key);
        }
        return node;
    }
}
