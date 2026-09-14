package az.millikart.pbl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import az.millikart.pbl.provider.ProviderOrderStatus;
import az.millikart.pbl.provider.ProviderOrderStatus.ProviderOrderOutcome;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

// P1-8a: словарь статусов заказа эквайера, без Spring. Таблица повторяет
// project_docs/TXPG-client-side-integration.md §5.8.8 плюс значения, унаследованные от исходного кода
// (что откуда — сказано в комментарии к ProviderOrderStatus).
class ProviderOrderStatusTest {

    // Каждое слово словаря даёт ровно тот исход, который предписан таблицей задачи.
    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            "FullyPaid,  PAID",
            "Cleared,    PAID",
            "Authorized, AUTHORIZED",
            "Rejected,   FAILED_FINAL",
            "Expired,    FAILED_FINAL",
            "Failed,     FAILED_FINAL",
            "Declined,   FAILED_FINAL",
            "Preparing,  NON_FINAL",
            "PartPaid,   SETTLED_OTHER",
            "Cancelled,  SETTLED_OTHER",
            "Canceled,   SETTLED_OTHER",
            "Refused,    SETTLED_OTHER",
            "Closed,     SETTLED_OTHER"
    })
    void knownStatuses_mapToTheirOutcome(String status, ProviderOrderOutcome expected) {
        assertEquals(expected, ProviderOrderStatus.classify(status));
    }

    @ParameterizedTest(name = "[{0}] -> UNKNOWN")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void nullAndBlank_areUnknown(String status) {
        assertEquals(ProviderOrderOutcome.UNKNOWN, ProviderOrderStatus.classify(status));
    }

    // Регистр и пробелы значимы: незнакомое написание — незнакомый статус.
    @ParameterizedTest(name = "\"{0}\" -> UNKNOWN")
    @ValueSource(strings = {"fullypaid", "FULLYPAID", " FullyPaid", "FullyPaid ", "rejected", "PREPARING"})
    void differentCaseOrPadding_isUnknown(String status) {
        assertEquals(ProviderOrderOutcome.UNKNOWN, ProviderOrderStatus.classify(status));
    }

    // Правдоподобные слова, которых нет в контракте, угадывать нельзя.
    @ParameterizedTest(name = "\"{0}\" -> UNKNOWN")
    @ValueSource(strings = {"Paid", "Settled", "Completed", "Success", "Void", "Reversed"})
    void plausibleButUnlisted_isUnknown(String status) {
        assertEquals(ProviderOrderOutcome.UNKNOWN, ProviderOrderStatus.classify(status));
    }

    // Не строка вовсе (число, карта, список) — это UNKNOWN, а не исключение.
    @Test
    void nonStringValues_areUnknown() {
        assertEquals(ProviderOrderOutcome.UNKNOWN, ProviderOrderStatus.classify(200));
        assertEquals(ProviderOrderOutcome.UNKNOWN, ProviderOrderStatus.classify(Map.of("value", "FullyPaid")));
        assertEquals(ProviderOrderOutcome.UNKNOWN, ProviderOrderStatus.classify(List.of("FullyPaid")));
        assertEquals(ProviderOrderOutcome.UNKNOWN, ProviderOrderStatus.classify(Boolean.TRUE));
    }

    // В контракте пишут с двумя l, в нашем старом коде с одной; оба обязаны лечь в одно место.
    @Test
    void cancelledAndCanceled_haveTheSameOutcome() {
        assertEquals(ProviderOrderStatus.classify("Cancelled"), ProviderOrderStatus.classify("Canceled"));
        assertEquals(ProviderOrderOutcome.SETTLED_OTHER, ProviderOrderStatus.classify("Cancelled"));
    }
}
