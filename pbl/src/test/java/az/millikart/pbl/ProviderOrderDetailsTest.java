package az.millikart.pbl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import az.millikart.pbl.provider.ProviderOrderDetails;
import az.millikart.pbl.provider.ProviderOrderDetails.TransactionFacts;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

// P1-16: маскированный номер карты, RRN и approval code читаются из payload заказа эквайера без
// Spring. Payload'ы взяты из контракта project_docs/TXPG-client-side-integration.md: §5.8.3 (lastTran),
// §5.8.4 (srcToken), §5.8.5/§5.8.6 (trans[]); правила выбора записи — §5.8.8 (Purchase /
// Purchase - Void / Refund, кто раньше — говорит regTime).
class ProviderOrderDetailsTest {

    // §5.8.4 / §5.8.6: srcToken.displayName.
    private static final String MASKED_CARD = "426863******3689";

    // §5.8.3 / §5.8.5 / §5.8.6: идентификаторы записи покупки.
    private static final String RRN = "629677123123123123";
    private static final String APPROVAL_CODE = "629677";

    private ListAppender<ILoggingEvent> logEvents;
    private Level previousLevel;

    @BeforeEach
    void captureLogs() {
        logEvents = new ListAppender<>();
        logEvents.start();
        previousLevel = detailsLogger().getLevel();
        detailsLogger().setLevel(Level.DEBUG);
        detailsLogger().addAppender(logEvents);
    }

    @AfterEach
    void releaseLogs() {
        detailsLogger().detachAppender(logEvents);
        detailsLogger().setLevel(previousLevel);
    }

    // Payload'ы из самого контракта

    // 1. §5.8.6 — все три уровня детализации: есть и trans[], и srcToken.
    @Test
    void fullResponse_yieldsAllThreeFacts() {
        TransactionFacts facts = ProviderOrderDetails.read(fullOrder());

        assertEquals(new TransactionFacts(MASKED_CARD, RRN, APPROVAL_CODE), facts);
    }

    // 2. §5.8.3 — только orderDetailLevel=2: lastTran вместо trans[].
    @Test
    void orderDetailLevelOnly_readsRrnAndApprovalCodeFromLastTran() {
        Map<String, Object> order = orderDetailLevel2();
        assertFalse(order.containsKey("trans"), "the fixture must mirror §5.8.3: no trans[]");

        TransactionFacts facts = ProviderOrderDetails.read(order);

        assertEquals(RRN, facts.rrn());
        assertEquals(APPROVAL_CODE, facts.approvalCode());
        assertNull(facts.maskedCard(), "§5.8.3 carries no srcToken");
    }

    // Какая запись trans[] выбирается (§5.8.8)

    // 3. В возвращённом заказе есть и покупка, и возврат; идентификаторы берутся у покупки.
    @Test
    void purchaseAndRefund_identifiersComeFromThePurchase() {
        Map<String, Object> order = orderWithTrans(
                record("Purchase", false, "2023-03-14 10:30:39", RRN, APPROVAL_CODE),
                record("Refund", false, "2023-03-15 09:00:00", "999999000000000001", "111111"));

        TransactionFacts facts = ProviderOrderDetails.read(order);

        assertEquals(RRN, facts.rrn());
        assertEquals(APPROVAL_CODE, facts.approvalCode());
    }

    // 4. Отменённый заказ: Purchase - Void — не покупка, что бы ни говорил его флаг.
    @Test
    void purchaseAndVoid_identifiersComeFromThePurchase() {
        Map<String, Object> order = orderWithTrans(
                record("Purchase - Void", false, "2023-03-14 10:35:00", "999999000000000002", "222222"),
                record("Purchase", false, "2023-03-14 10:30:39", RRN, APPROVAL_CODE));

        TransactionFacts facts = ProviderOrderDetails.read(order);

        assertEquals(RRN, facts.rrn());
        assertEquals(APPROVAL_CODE, facts.approvalCode());
    }

    // 5. isReversal: true пропускается, даже если description выглядит покупкой.
    @Test
    void reversalFlag_isSkipped() {
        Map<String, Object> order = orderWithTrans(
                record("Purchase", true, "2023-03-14 10:29:00", "999999000000000003", "333333"),
                record("Purchase", false, "2023-03-14 10:30:39", RRN, APPROVAL_CODE));

        TransactionFacts facts = ProviderOrderDetails.read(order);

        assertEquals(RRN, facts.rrn());
        assertEquals(APPROVAL_CODE, facts.approvalCode());
    }

    // 6. Две покупки: побеждает самая ранняя по regTime, порядок в списке не важен.
    @Test
    void twoPurchases_theEarliestByRegTimeWins() {
        Map<String, Object> order = orderWithTrans(
                record("Purchase", false, "2023-03-14 11:00:00", "999999000000000004", "444444"),
                record("Purchase", false, "2023-03-14 10:30:39", RRN, APPROVAL_CODE),
                record("Purchase", false, "2023-03-14 10:45:00", "999999000000000005", "555555"));

        TransactionFacts facts = ProviderOrderDetails.read(order);

        assertEquals(RRN, facts.rrn());
        assertEquals(APPROVAL_CODE, facts.approvalCode());
    }

    // 7. Случай DMS, которого нет в контракте: записи без description. Предпочтению Purchase
    // нечего предпочитать, поэтому берётся самая ранняя запись без реверса.
    @Test
    void recordsWithoutDescription_theEarliestNonReversalWins() {
        Map<String, Object> order = orderWithTrans(
                record(null, true, "2023-03-14 10:00:00", "999999000000000006", "666666"),
                record(null, false, "2023-03-14 10:40:00", "999999000000000007", "777777"),
                record(null, false, "2023-03-14 10:30:39", RRN, APPROVAL_CODE));

        TransactionFacts facts = ProviderOrderDetails.read(order);

        assertEquals(RRN, facts.rrn());
        assertEquals(APPROVAL_CODE, facts.approvalCode());
    }

    // 8. Только возвраты и реверсы: показывать нечего, но и исключения нет.
    @Test
    void onlyRefundsAndReversals_yieldsNoIdentifiers() {
        Map<String, Object> order = orderWithTrans(
                record("Refund", false, "2023-03-15 09:00:00", "999999000000000008", "888888"),
                record("Purchase - Void", true, "2023-03-14 10:35:00", "999999000000000009", "999999"));

        TransactionFacts facts = assertDoesNotThrow(() -> ProviderOrderDetails.read(order));

        assertNull(facts.rrn());
        assertNull(facts.approvalCode());
        assertEquals(MASKED_CARD, facts.maskedCard(), "the card is still known");
    }

    // 9. Ни trans[], ни lastTran — payload заказа, который ещё никто не оплатил.
    @Test
    void neitherTransNorLastTran_yieldsNoIdentifiers() {
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("id", 11338);
        order.put("status", "Preparing");
        order.put("srcToken", contractSrcToken());

        TransactionFacts facts = ProviderOrderDetails.read(order);

        assertEquals(new TransactionFacts(MASKED_CARD, null, null), facts);
    }

    // lastTran проходит те же фильтры: последняя операция возвращённого заказа — возврат.
    @Test
    void lastTranThatIsARefund_isNotTheSource() {
        Map<String, Object> order = orderDetailLevel2();
        order.put("lastTran", record("Refund", false, "2023-03-15 09:00:00", "999999000000000010", "101010"));

        TransactionFacts facts = ProviderOrderDetails.read(order);

        assertNull(facts.rrn());
        assertNull(facts.approvalCode());
    }

    // Маскированная карта

    // 10. Нет srcToken: пуста только маска, оба идентификатора на месте.
    @Test
    void noSrcToken_onlyTheMaskIsEmpty() {
        Map<String, Object> order = fullOrder();
        order.remove("srcToken");

        TransactionFacts facts = ProviderOrderDetails.read(order);

        assertEquals(new TransactionFacts(null, RRN, APPROVAL_CODE), facts);
    }

    // 11. srcToken без displayName.
    @Test
    void srcTokenWithoutDisplayName_maskIsEmpty() {
        Map<String, Object> order = fullOrder();
        Map<String, Object> srcToken = contractSrcToken();
        srcToken.remove("displayName");
        order.put("srcToken", srcToken);

        TransactionFacts facts = ProviderOrderDetails.read(order);

        assertNull(facts.maskedCard());
        assertEquals(RRN, facts.rrn());
    }

    // Маска возвращается как прислал эквайер: последние четыре цифры фронт берёт сам.
    @Test
    void mask_isNotReformatted() {
        assertEquals("426863******3689", ProviderOrderDetails.read(fullOrder()).maskedCard());
    }

    // Правило скаляра (ProviderPayloads.scalarText)

    // 12. Шлюз, сериализующий RRN числом, всё равно называет операцию.
    @Test
    void rrnAsNumber_isReturnedAsText() {
        Map<String, Object> order = orderWithTrans(record("Purchase", false, "2023-03-14 10:30:39", 629677123123123123L, APPROVAL_CODE));

        assertEquals("629677123123123123", ProviderOrderDetails.read(order).rrn());
    }

    // 13. Структура вместо RRN — это «нет значения», а не "{...}" на экране мерчанта.
    @Test
    void rrnAsObject_isEmpty() {
        Map<String, Object> order = orderWithTrans(record("Purchase", false, "2023-03-14 10:30:39", Map.of("id", "x"), APPROVAL_CODE));

        TransactionFacts facts = ProviderOrderDetails.read(order);

        assertNull(facts.rrn());
        assertEquals(APPROVAL_CODE, facts.approvalCode(), "the other field of the same record is unaffected");
    }

    // Пробельная строка — тоже отсутствие: пустой id ничего не идентифицирует.
    @Test
    void blankApprovalCode_isEmpty() {
        Map<String, Object> order = orderWithTrans(record("Purchase", false, "2023-03-14 10:30:39", RRN, "   "));

        assertNull(ProviderOrderDetails.read(order).approvalCode());
    }

    // Оборонительный разбор: ничто здесь не имеет права бросить исключение

    // 14. trans не список, элементы не карты, payload вовсе отсутствует.
    @Test
    void malformedPayloads_yieldEmptyFactsWithoutThrowing() {
        assertEquals(new TransactionFacts(null, null, null), assertDoesNotThrow(() -> ProviderOrderDetails.read(null)));

        Map<String, Object> transIsAString = new LinkedHashMap<>();
        transIsAString.put("trans", "Purchase");
        assertEquals(new TransactionFacts(null, null, null), assertDoesNotThrow(() -> ProviderOrderDetails.read(transIsAString)));

        Map<String, Object> transIsAMap = new LinkedHashMap<>();
        transIsAMap.put("trans", contractPurchase());
        assertEquals(new TransactionFacts(null, null, null), assertDoesNotThrow(() -> ProviderOrderDetails.read(transIsAMap)));

        Map<String, Object> elementsAreNotMaps = orderWithTrans("Purchase", 7, null, List.of("rrn"));
        assertEquals(new TransactionFacts(MASKED_CARD, null, null), assertDoesNotThrow(() -> ProviderOrderDetails.read(elementsAreNotMaps)));

        Map<String, Object> srcTokenIsAString = fullOrder();
        srcTokenIsAString.put("srcToken", MASKED_CARD);
        assertEquals(new TransactionFacts(null, RRN, APPROVAL_CODE), assertDoesNotThrow(() -> ProviderOrderDetails.read(srcTokenIsAString)));

        Map<String, Object> lastTranIsAList = new LinkedHashMap<>();
        lastTranIsAList.put("lastTran", List.of(contractPurchase()));
        assertEquals(new TransactionFacts(null, null, null), assertDoesNotThrow(() -> ProviderOrderDetails.read(lastTranIsAList)));
    }

    // Элемент-карта среди не-карт всё равно читается, мусор вокруг пропускается.
    @Test
    void nonMapElements_areSkippedNotFatal() {
        Map<String, Object> order = orderWithTrans("Purchase", 7, null, List.of("rrn"),
                record("Purchase", false, "2023-03-14 10:30:39", RRN, APPROVAL_CODE));

        TransactionFacts facts = assertDoesNotThrow(() -> ProviderOrderDetails.read(order));

        assertEquals(RRN, facts.rrn());
        assertEquals(APPROVAL_CODE, facts.approvalCode());
    }

    // isReversal строкой "true" — тоже реверс; что-то невнятное реверсом не считается.
    @Test
    void reversalFlag_asStringOrGarbage() {
        Map<String, Object> flagIsAString = record("Purchase", false, "2023-03-14 10:29:00", "999999000000000011", "111000");
        flagIsAString.put("isReversal", "true");
        Map<String, Object> flagIsGarbage = record("Purchase", false, "2023-03-14 10:30:39", RRN, APPROVAL_CODE);
        flagIsGarbage.put("isReversal", Map.of());

        TransactionFacts facts = assertDoesNotThrow(() -> ProviderOrderDetails.read(orderWithTrans(flagIsAString, flagIsGarbage)));

        assertEquals(RRN, facts.rrn());
        assertEquals(APPROVAL_CODE, facts.approvalCode());
    }

    // Запись с regTime бьёт запись без него; странное значение не разбирают — оно проигрывает.
    @Test
    void regTimeMissingOrOdd_doesNotThrow() {
        Map<String, Object> noRegTime = record("Purchase", false, null, "999999000000000012", "121212");
        Map<String, Object> oddRegTime = record("Purchase", false, "yesterday", "999999000000000013", "131313");
        Map<String, Object> properRegTime = record("Purchase", false, "2023-03-14 10:30:39", RRN, APPROVAL_CODE);

        TransactionFacts facts = assertDoesNotThrow(() -> ProviderOrderDetails.read(orderWithTrans(noRegTime, oddRegTime, properRegTime)));

        assertEquals(RRN, facts.rrn(), "'2023-…' sorts before 'yesterday' and before no value at all");
    }

    // Строка DEBUG

    // Ничего не нашли — DEBUG-строка с тем, что было в payload, и никогда пароль заказа (P0-9).
    @Test
    void nothingFound_logsWhatThePayloadHadWithoutThePassword() {
        Map<String, Object> order = orderWithTrans(
                record("Refund", false, "2023-03-15 09:00:00", "999999000000000008", "888888"));
        order.put("password", "1h1pq153fk8xk");
        Map<String, Object> lastTran = record("Refund", false, "2023-03-15 09:00:00", "999999000000000008", "888888");
        lastTran.put("password", "should-not-be-there-but-is-stripped-anyway");
        order.put("lastTran", lastTran);

        ProviderOrderDetails.read(order);

        List<String> debugLines = logEvents.list.stream()
                .filter(event -> event.getLevel() == Level.DEBUG)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
        assertEquals(1, debugLines.size(), "exactly one DEBUG line: " + debugLines);
        String line = debugLines.getFirst();
        assertTrue(line.contains("11338"), "names the order: " + line);
        assertTrue(line.contains("Refund"), "says what was there: " + line);
        assertFalse(line.contains("1h1pq153fk8xk"), "no password: " + line);
        assertFalse(line.contains("should-not-be-there"), "no password, even nested: " + line);
    }

    // Нашли — DEBUG-строки нет вовсе: путь чтения списка обязан молчать.
    @Test
    void found_logsNothing() {
        ProviderOrderDetails.read(fullOrder());

        assertTrue(logEvents.list.isEmpty(), "got: " + logEvents.list);
    }

    // Фикстуры: payload'ы контракта дословно

    // §5.8.6 — объект order оплаченного SMS-заказа со всеми тремя уровнями детализации. Поля,
    // которые класс не читает (terminal, merchant, type и прочие), опущены; password оставлен
    // намеренно: настоящий payload его несёт, а парсеру должно быть всё равно.
    private static Map<String, Object> fullOrder() {
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("id", 11338);
        order.put("hppUrl", "https://test.millikart.az:8004");
        order.put("password", "1h1pq153fk8xk");
        order.put("status", "FullyPaid");
        order.put("ridByMerchant", "123123871283618376123");
        order.put("prevStatus", "Preparing");
        order.put("lastStatusLogin", "Admin");
        order.put("amount", 5);
        order.put("currency", "AZN");
        order.put("createTime", "2023-03-14 10:31:23");
        order.put("storedTokens", List.of(Map.of("id", 9217)));
        order.put("trans", new ArrayList<>(List.of(contractPurchase())));
        order.put("cvv2AuthStatus", "Provided");
        order.put("authorizedChargeAmount", 5);
        order.put("clearedChargeAmount", 5);
        order.put("clearedRefundAmount", 0);
        order.put("description", " test order");
        order.put("language", "en");
        order.put("srcToken", contractSrcToken());
        order.put("custAttrs", List.of(
                Map.of("rid", "PrevStatus", "valAsStr", "Preparing"),
                Map.of("rid", "PmoResultCode", "valAsStr", "Approved")));
        return order;
    }

    // §5.8.3 — только orderDetailLevel=2: lastTran, без trans[] и без srcToken.
    private static Map<String, Object> orderDetailLevel2() {
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("id", 11338);
        order.put("hppUrl", "https://test.millikart.az:8004");
        order.put("password", "1h1pq153fk8xk");
        order.put("status", "FullyPaid");
        order.put("ridByMerchant", "123123871283618376123");
        order.put("prevStatus", "Preparing");
        order.put("amount", 5);
        order.put("currency", "AZN");
        order.put("createTime", "2023-03-14 10:31:23");
        order.put("lastTran", contractPurchase());
        order.put("storedTokens", List.of(Map.of("id", 9217)));
        order.put("custAttrs", List.of(
                Map.of("rid", "PrevStatus", "valAsStr", "Preparing"),
                Map.of("rid", "PmoResultCode", "valAsStr", "Approved")));
        return order;
    }

    // §5.8.5 / §5.8.6 (и поле в поле lastTran из §5.8.3): запись покупки.
    private static Map<String, Object> contractPurchase() {
        Map<String, Object> tran = new LinkedHashMap<>();
        tran.put("approvalCode", APPROVAL_CODE);
        tran.put("actionId", "230314-06303941-0024iz=");
        tran.put("orderId", 11338);
        tran.put("terminalId", 1);
        tran.put("merchantId", 1);
        tran.put("billingStatus", "Normal");
        tran.put("isReversal", false);
        tran.put("ridByAcquirer", "230314000000002720");
        tran.put("ridByPmo", "230314000000002720");
        tran.put("regTime", "2023-03-14 10:30:39");
        tran.put("clearDay", "2020-06-18");
        tran.put("clearAmount", 5);
        tran.put("clearCcy", "AZN");
        tran.put("amount", 5);
        tran.put("rrn", RRN);
        tran.put("currency", "AZN");
        tran.put("description", "Purchase");
        tran.put("phase", "Single");
        tran.put("type", "Purchase");
        return tran;
    }

    // §5.8.4 / §5.8.6: srcToken — карта, уже замаскированная эквайером.
    private static Map<String, Object> contractSrcToken() {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("authentication", Map.of("needCvv2", false, "needTds", false));
        card.put("expiration", "0131");
        card.put("brand", "Visa");
        card.put("restoredFromId", 9217);
        card.put("issuerRid", "4268");
        Map<String, Object> token = new LinkedHashMap<>();
        token.put("id", 9216);
        token.put("paymentMethod", "Card");
        token.put("role", "Src");
        token.put("status", "Active");
        token.put("regTime", "2023-03-14 10:31:30");
        token.put("entryMode", "ECommerce");
        token.put("displayName", MASKED_CARD);
        token.put("owner", Map.of());
        token.put("card", card);
        return token;
    }

    // Запись транзакции по образцу contractPurchase() с подменёнными полями, влияющими на выбор.
    // description == null убирает ключ (случай DMS), regTime == null убирает свой; rrn и
    // approvalCode кладутся как есть, любого типа — так тесты правила скаляра получают число
    // или карту вместо строки.
    private static Map<String, Object> record(String description, boolean isReversal, String regTime,
                                              Object rrn, Object approvalCode) {
        Map<String, Object> tran = contractPurchase();
        if (description == null) {
            tran.remove("description");
        } else {
            tran.put("description", description);
        }
        tran.put("isReversal", isReversal);
        if (regTime == null) {
            tran.remove("regTime");
        } else {
            tran.put("regTime", regTime);
        }
        tran.put("rrn", rrn);
        tran.put("approvalCode", approvalCode);
        return tran;
    }

    // Заказ §5.8.6, у которого trans[] заменён переданными элементами (картами или мусором).
    private static Map<String, Object> orderWithTrans(Object... trans) {
        Map<String, Object> order = fullOrder();
        order.put("trans", Arrays.asList(trans));
        return order;
    }

    private static Logger detailsLogger() {
        return (Logger) LoggerFactory.getLogger(ProviderOrderDetails.class);
    }
}
