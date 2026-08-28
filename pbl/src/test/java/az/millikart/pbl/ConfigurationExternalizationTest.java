package az.millikart.pbl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

// P1-10: сторожит продовую конфигурацию от возвращения адресов конкретного окружения. Раньше
// pbl.base-url был http://localhost:8080/, а адреса провайдера смотрели на тестовый стенд —
// литералами в application.yaml. Видимо не ломается ничего: сервис стартует, ссылки создаются, а
// в проде эквайер возвращает плательщика на localhost или деньги уходят тестовому эквайеру.
class ConfigurationExternalizationTest {

    // Читаем с файловой системы, а не с classpath: копия на classpath — тот же файл, но чтение
    // оттуда могло бы молча подхватить одноимённый тестовый ресурс. Тестовый yaml не покрывается
    // намеренно: в нём литеральные example.com, чтобы прогон не зависел от окружения машины.
    private static final Path PRODUCTION_YAML = Path.of("src", "main", "resources", "application.yaml");

    @Test
    void productionYaml_doesNotHardcodeTheAcquirerHost() throws IOException {
        String yaml = read();

        Assertions.assertFalse(yaml.contains("millikart.az"),
                "pbl/src/main/resources/application.yaml must not contain a MilliKart address: the acquirer "
                        + "addresses come from PBL_PROVIDER_GATEWAY_BASE_URL / PBL_PROVIDER_API_BASE_URL "
                        + "with no default (P1-10, Р-18)");
    }

    @Test
    void productionYaml_doesNotHardcodeTheLocalBaseUrl() throws IOException {
        String yaml = read();

        Assertions.assertFalse(yaml.contains("localhost:8080"),
                "pbl/src/main/resources/application.yaml must not contain localhost:8080: pbl.base-url is "
                        + "handed to the acquirer as hppRedirectUrl and comes from PBL_BASE_URL with no "
                        + "default (P1-10, Р-16)");
    }

    // Два сторожа выше ловят адреса, которые там были; этот ловит следующую форму той же ошибки —
    // умолчание, протащенное через сам плейсхолдер: ${PBL_BASE_URL:http://127.0.0.1:8080/} проходит
    // обе проверки подстрок. Каждый из трёх адресов обязан быть ровно ${VARIABLE}, без : в скобках.
    @Test
    void productionYaml_readsTheThreeAddressesFromTheEnvironmentWithoutDefaults() throws IOException {
        String yaml = read();

        assertExactPlaceholder(yaml, "base-url", "PBL_BASE_URL");
        assertExactPlaceholder(yaml, "gateway-base-url", "PBL_PROVIDER_GATEWAY_BASE_URL");
        assertExactPlaceholder(yaml, "api-base-url", "PBL_PROVIDER_API_BASE_URL");
    }

    private static void assertExactPlaceholder(String yaml, String key, String variable) {
        Pattern exact = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + ":\\s*\\$\\{" + variable + "}\\s*$");
        Assertions.assertTrue(exact.matcher(yaml).find(),
                key + " must be exactly ${" + variable + "} — no default inside the placeholder (P1-10)");
    }

    private static String read() throws IOException {
        Assertions.assertTrue(Files.exists(PRODUCTION_YAML),
                "expected to run with the pbl module directory as the working directory; "
                        + PRODUCTION_YAML.toAbsolutePath() + " not found");
        return Files.readString(PRODUCTION_YAML, StandardCharsets.UTF_8);
    }
}
