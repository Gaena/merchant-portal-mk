package az.millikart.pbl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import az.millikart.pbl.provider.ProviderPayloads;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

// P0-9: два взгляда на данные эквайера, которые могут попасть в лог или в базу, без Spring.
// Пароль заказа едет в query каждого следующего вызова (Р-25) и лежит на верхнем уровне payload
// (§5.8.3) — эти функции держат его вне лога и вне provider_response. Плюс единственное правило
// о том, что считать значением в payload (scalarText, P1-8b → P1-16): второй копии быть не должно.
class ProviderPayloadsTest {

    private static final String PASSWORD = "1h1pq153fk8xk";

    // urlForLog

    // Форма любого опроса статуса: сначала пароль, затем константы уровней детализации.
    @Test
    void urlForLog_dropsTheWholeQueryString() {
        String url = "https://api.txpg.example.com/order/11338?password=" + PASSWORD
                + "&orderDetailLevel=2&tokenDetailLevel=2&tranDetailLevel=2";

        String logged = ProviderPayloads.urlForLog(url);

        assertEquals("https://api.txpg.example.com/order/11338", logged);
        assertFalse(logged.contains("password"), logged);
        assertFalse(logged.contains(PASSWORD), logged);
    }

    // У createEcomOrder нет query: адрес логируется как есть.
    @Test
    void urlForLog_leavesAnAddressWithoutQueryStringAsItIs() {
        assertEquals("https://gateway.txpg.example.com/order",
                ProviderPayloads.urlForLog("https://gateway.txpg.example.com/order"));
    }

    // Почему выбрасывают весь query, а не маскируют один параметр: replaceQueryParam("password",
    // "***") добавил бы параметр там, где его не было, и лог утверждал бы, что пароль отправляли
    // в вызове, который его не слал.
    @Test
    void urlForLog_doesNotInventAPasswordMarkerWhereThereWasNoQuery() {
        String logged = ProviderPayloads.urlForLog("https://gateway.txpg.example.com/order");

        assertFalse(logged.contains("password"), logged);
        assertFalse(logged.contains("?"), logged);
    }

    // Редирект плательщика: адрес HPP с приписанными ?id=…&password=…
    @Test
    void urlForLog_stripsThePayerRedirect() {
        String logged = ProviderPayloads.urlForLog("https://test.millikart.az:8004?id=11338&password=" + PASSWORD);

        assertEquals("https://test.millikart.az:8004", logged);
    }

    // Логирование не имеет права упасть из-за того, что собиралось записать.
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"не-url", "http://", "http://[::1", "   ", "?password=" + PASSWORD})
    void urlForLog_neverThrows(String url) {
        assertDoesNotThrow(() -> ProviderPayloads.urlForLog(url));
    }

    // null и "" возвращаются как есть — вырезать в них нечего.
    @Test
    void urlForLog_keepsNullAndEmpty() {
        assertNull(ProviderPayloads.urlForLog(null));
        assertEquals("", ProviderPayloads.urlForLog(""));
    }

    // Неразобранный адрес заменяется заглушкой, а не возвращается как пришёл: строку, которую не
    // смогли прочитать, нельзя считать свободной от пароля.
    @ParameterizedTest
    @ValueSource(strings = {"http://", "http://[::1"})
    void urlForLog_unparseableAddress_isAPlaceholderNotTheInput(String url) {
        String logged = ProviderPayloads.urlForLog(url);

        assertNotEquals(url, logged);
        assertFalse(logged.contains("["), "the raw input must not leak through: " + logged);
    }

    // Что бы парсер ни сделал со строкой, не являющейся URL, секретная часть уцелеть не должна.
    @Test
    void urlForLog_queryOnlyString_losesThePassword() {
        String logged = ProviderPayloads.urlForLog("?password=" + PASSWORD);

        assertFalse(logged.contains(PASSWORD), logged);
    }

    // У opaque URI «query» лежит внутри scheme-specific части, которую replaceQuery из
    // UriComponentsBuilder не трогает. Сервис такого не логирует, но гарантия держится и здесь.
    @Test
    void urlForLog_opaqueUri_losesThePasswordToo() {
        String logged = ProviderPayloads.urlForLog("mailto:ops@example.com?password=" + PASSWORD);

        assertFalse(logged.contains(PASSWORD), logged);
        assertFalse(logged.contains("?"), logged);
    }

    // withoutSecrets

    // Объект order из §5.8.3: пароль уходит, всё остальное остаётся, включая вложенное.
    @Test
    void withoutSecrets_removesThePasswordAndKeepsTheRest() {
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("id", 11338);
        order.put("hppUrl", "https://test.millikart.az:8004");
        order.put("password", PASSWORD);
        order.put("status", "FullyPaid");
        order.put("amount", 5);
        order.put("custAttrs", List.of(Map.of("rid", "PmoResultCode", "valAsStr", "Approved")));

        Map<String, Object> cleaned = ProviderPayloads.withoutSecrets(order);

        assertFalse(cleaned.containsKey("password"), cleaned.toString());
        assertEquals(11338, cleaned.get("id"));
        assertEquals("https://test.millikart.az:8004", cleaned.get("hppUrl"));
        assertEquals("FullyPaid", cleaned.get("status"));
        assertEquals(5, cleaned.get("amount"));
        assertEquals(order.get("custAttrs"), cleaned.get("custAttrs"));
        assertFalse(cleaned.toString().contains(PASSWORD), cleaned.toString());
    }

    // Вход не меняется: неизменяемая карта обязана работать, а оригинал сохраняет свой ключ.
    @Test
    void withoutSecrets_doesNotModifyTheInput() {
        Map<String, Object> immutable = Map.of("id", 11338L, "password", PASSWORD, "status", "Preparing");

        Map<String, Object> cleaned = assertDoesNotThrow(() -> ProviderPayloads.withoutSecrets(immutable));

        assertNotSame(immutable, cleaned);
        assertEquals(PASSWORD, immutable.get("password"), "the input map must keep its key");
        assertFalse(cleaned.containsKey("password"));
        assertEquals(2, cleaned.size());
    }

    // Payload без этого ключа копируется как есть.
    @Test
    void withoutSecrets_payloadWithoutPassword_isUnchanged() {
        Map<String, Object> tran = Map.of("tran", Map.of("approvalCode", "340775"));

        assertEquals(tran, ProviderPayloads.withoutSecrets(tran));
    }

    // Оборонительно: отсутствующий payload не бросает и остаётся отсутствующим.
    @Test
    void withoutSecrets_null_doesNotThrow() {
        assertNull(assertDoesNotThrow(() -> ProviderPayloads.withoutSecrets(null)));
    }

    // Копия — рабочая карта: сервисы кладут поверх неё свои маркеры.
    @Test
    void withoutSecrets_returnsAMutableCopy() {
        Map<String, Object> cleaned = ProviderPayloads.withoutSecrets(Map.of("status", "Preparing"));

        assertDoesNotThrow(() -> cleaned.put("mpStatusOutcome", "NON_FINAL"));
        assertEquals("NON_FINAL", cleaned.get("mpStatusOutcome"));
    }

    // scalarText

    // Значения из самого контракта: идентификаторы — строки.
    @Test
    void scalarText_keepsAString() {
        assertEquals("220613334596244733", ProviderPayloads.scalarText("220613334596244733"));
        assertEquals("426863******3689", ProviderPayloads.scalarText("426863******3689"));
        assertEquals("Purchase - Void", ProviderPayloads.scalarText("Purchase - Void"));
    }

    // Шлюз, сериализующий идентификатор числом, всё равно называет операцию.
    @ParameterizedTest
    @MethodSource("numbers")
    void scalarText_keepsANumberAsText(Number value, String expected) {
        assertEquals(expected, ProviderPayloads.scalarText(value));
    }

    static Stream<Arguments> numbers() {
        return Stream.of(
                Arguments.of(220613334596244733L, "220613334596244733"),
                Arguments.of(629677, "629677"),
                Arguments.of(new BigDecimal("629677123123123123"), "629677123123123123"));
    }

    // Пробельная строка — отсутствие: пустой id ничего не идентифицирует.
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t"})
    void scalarText_blankOrNull_isAbsent(String value) {
        assertNull(ProviderPayloads.scalarText(value));
    }

    // Структура вместо значения — «нет значения», а не java-представление: String.valueOf карты
    // дал бы непустое "{a=1}", которое однажды сошло за подтверждённую денежную операцию (P1-8b),
    // а теперь показалось бы как RRN на экране мерчанта (P1-16).
    @ParameterizedTest
    @MethodSource("structures")
    void scalarText_structureOrBoolean_isAbsent(Object value) {
        assertNull(ProviderPayloads.scalarText(value), "got a value for: " + value);
    }

    static Stream<Object> structures() {
        return Stream.of(Map.of(), Map.of("id", "x"), List.of(), List.of("x"), Boolean.TRUE, Boolean.FALSE);
    }
}
