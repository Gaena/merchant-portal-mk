package az.millikart.pbl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import az.millikart.txpg.AcquirerDeclinedException;
import az.millikart.txpg.AcquiringClient;
import az.millikart.txpg.TxpgAcquiringClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

// Устойчивость к эквайеру без Spring. Тестовый application.yaml заменяет боевой и resilience4j не содержит,
// а клиент в тестах — мок без аспектов, поэтому ни один прогон не видит ни ignoreExceptions, ни того, на
// каких вызовах висит breaker. @Retry сторожит MoneyOperationsIntegrationTest.
class AcquirerResilienceConfigurationTest {

    // Отказ шлюза терминалу — не сбой шлюза: засчитай его breaker, и один терминал с неверными кредами
    // закроет приём платежей всем; повтор отказа даёт тот же отказ. Класс назван в yaml строкой —
    // переименование без правки yaml сломало бы только боевой старт.
    @Test
    void acquirerDeclines_neitherTripTheBreakerNorAreRetried() throws IOException {
        Map<String, Object> yaml = productionYaml();
        List<String> declined = List.of(AcquirerDeclinedException.class.getName());

        assertEquals(declined, value(yaml, "resilience4j.circuitbreaker.instances.acquiring.ignoreExceptions"));
        assertEquals(declined, value(yaml, "resilience4j.retry.instances.acquiring.ignoreExceptions"));
    }

    // Breaker — на четырёх боевых вызовах, все под одним именем, чьи настройки выше. На «Тесте» его нет (Р-70):
    // проверки администратора по терминалам с неверными кредами разомкнули бы общий breaker для всех платежей.
    @Test
    void theBreakerGuardsThePaymentCalls_andNotTheTerminalCheck() {
        Map<String, String> guarded = Arrays.stream(TxpgAcquiringClient.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(CircuitBreaker.class))
                .collect(Collectors.toMap(Method::getName, method -> method.getAnnotation(CircuitBreaker.class).name()));

        assertEquals(Set.of("createEcomOrder", "completeDms", "refund", "getOrderStatus"), guarded.keySet());
        assertTrue(guarded.values().stream().allMatch("acquiring"::equals), String.valueOf(guarded));
        // На классе или интерфейсе breaker накрыл бы и checkOrderCreation.
        assertFalse(TxpgAcquiringClient.class.isAnnotationPresent(CircuitBreaker.class));
        assertFalse(AcquiringClient.class.isAnnotationPresent(CircuitBreaker.class));
    }

    // С файловой системы, а не с classpath: там одноимённый тестовый yaml (см. ConfigurationExternalizationTest).
    private static Map<String, Object> productionYaml() throws IOException {
        return new Yaml().load(Files.readString(Path.of("src", "main", "resources", "application.yaml"), StandardCharsets.UTF_8));
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
