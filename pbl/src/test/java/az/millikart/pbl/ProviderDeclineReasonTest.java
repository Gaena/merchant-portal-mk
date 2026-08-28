package az.millikart.pbl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import az.millikart.pbl.provider.ProviderDeclineReason;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// P1-8b: чтение причины отказа эквайера из custAttrs, без Spring. Порядок поиска повторяет
// pbl/TXPG-client-side-integration.md §5.8.7; исключение для Approved — из §5.8.3, где успешный
// заказ несёт PmoResultCode: Approved.
class ProviderDeclineReasonTest {

    // §5.8.7, шаг 1: побеждает собственное описание модуля e-commerce.
    @Test
    void declineDescription_isReturnedFirst() {
        Map<String, Object> order = orderWith(
                attr("PrevStatus", "Preparing"),
                attr("PmoResultCode", "05"),
                attr("PmoDeclineDescription", "Invalid cvv2 for this card."),
                attr("DeclineDescription", "Invalid PAN"));

        assertEquals(Optional.of("Invalid PAN"), ProviderDeclineReason.extract(order));
    }

    // §5.8.7: без DeclineDescription берётся описание ядровой системы.
    @Test
    void pmoDeclineDescription_isReturnedWhenThereIsNoDeclineDescription() {
        Map<String, Object> order = orderWith(
                attr("PmoResultCode", "05"),
                attr("PmoDeclineDescription", "Invalid cvv2 for this card."));

        assertEquals(Optional.of("Invalid cvv2 for this card."), ProviderDeclineReason.extract(order));
    }

    // §5.8.3: успешный заказ тоже несёт PmoResultCode: Approved. Это не причина отказа и не имеет
    // права всплыть как причина.
    @Test
    void pmoResultCodeApproved_isNotAReason() {
        Map<String, Object> order = orderWith(
                attr("PrevStatus", "Preparing"),
                attr("PmoResultCode", "Approved"));

        assertEquals(Optional.empty(), ProviderDeclineReason.extract(order));
    }

    // Любой другой PmoResultCode — последняя надежда, возвращается как есть.
    @Test
    void pmoResultCodeOtherThanApproved_isReturnedAsLastResort() {
        Map<String, Object> order = orderWith(attr("PmoResultCode", "51"));

        assertEquals(Optional.of("51"), ProviderDeclineReason.extract(order));
    }

    // DeclineDescription всё равно бьёт PmoResultCode, отличный от Approved.
    @Test
    void declineDescription_winsOverPmoResultCode() {
        Map<String, Object> order = orderWith(
                attr("PmoResultCode", "51"),
                attr("DeclineDescription", "Insufficient funds"));

        assertEquals(Optional.of("Insufficient funds"), ProviderDeclineReason.extract(order));
    }

    // Оборонительный разбор: ничто здесь не имеет права бросить исключение

    @Test
    void nullOrder_isEmpty() {
        assertEquals(Optional.empty(), ProviderDeclineReason.extract(null));
    }

    @Test
    void missingCustAttrs_isEmpty() {
        assertEquals(Optional.empty(), ProviderDeclineReason.extract(Map.of("status", "Rejected")));
    }

    @Test
    void custAttrsThatIsNotAList_isEmpty() {
        assertEquals(Optional.empty(), ProviderDeclineReason.extract(Map.of("custAttrs", "DeclineDescription")));
        assertEquals(Optional.empty(), ProviderDeclineReason.extract(Map.of("custAttrs", 42)));
        assertEquals(Optional.empty(), ProviderDeclineReason.extract(Map.of("custAttrs",
                Map.of("rid", "DeclineDescription", "valAsStr", "Invalid PAN"))));
    }

    @Test
    void elementsThatAreNotMaps_areSkippedNotFatal() {
        Map<String, Object> order = orderWith("DeclineDescription", 7, null, List.of("rid"),
                attr("DeclineDescription", "Invalid PAN"));

        assertEquals(Optional.of("Invalid PAN"), ProviderDeclineReason.extract(order));
    }

    @Test
    void emptyList_isEmpty() {
        assertEquals(Optional.empty(), ProviderDeclineReason.extract(Map.of("custAttrs", List.of())));
    }

    // Нестроковый valAsStr — тоже текст; пустой или отсутствующий равносилен отсутствию.
    @Test
    void valAsStrThatIsNotAString_isKeptAsText() {
        Map<String, Object> order = orderWith(attr("PmoResultCode", 51));

        assertEquals(Optional.of("51"), ProviderDeclineReason.extract(order));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankValAsStr_isSkipped(String blank) {
        Map<String, Object> order = orderWith(
                attr("DeclineDescription", blank),
                attr("PmoDeclineDescription", "Invalid cvv2 for this card."));

        assertEquals(Optional.of("Invalid cvv2 for this card."), ProviderDeclineReason.extract(order));
    }

    @Test
    void attributeWithoutValAsStr_isSkipped() {
        Map<String, Object> noValue = new HashMap<>();
        noValue.put("rid", "DeclineDescription");
        Map<String, Object> nullValue = new HashMap<>();
        nullValue.put("rid", "DeclineDescription");
        nullValue.put("valAsStr", null);
        Map<String, Object> order = orderWith(noValue, nullValue, attr("PmoResultCode", "05"));

        assertEquals(Optional.of("05"), ProviderDeclineReason.extract(order));
    }

    // Прочие атрибуты из контракта нельзя принимать за причину отказа.
    @Test
    void unrelatedAttributesOnly_isEmpty() {
        Map<String, Object> order = orderWith(attr("PrevStatus", "Preparing"), attr("Something", "else"));

        Optional<String> reason = ProviderDeclineReason.extract(order);
        assertTrue(reason.isEmpty(), "got: " + reason);
    }

    // Фикстуры

    private static Map<String, Object> orderWith(Object... custAttrs) {
        Map<String, Object> order = new HashMap<>();
        order.put("status", "Rejected");
        order.put("custAttrs", java.util.Arrays.asList(custAttrs));
        return order;
    }

    private static Map<String, Object> attr(String rid, Object valAsStr) {
        Map<String, Object> attr = new HashMap<>();
        attr.put("rid", rid);
        attr.put("valAsStr", valAsStr);
        return attr;
    }
}
