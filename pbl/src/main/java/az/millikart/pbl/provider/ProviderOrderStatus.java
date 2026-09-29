package az.millikart.pbl.provider;

import java.util.Map;

// Словарь order.status эквайера (§5.8.8). Полноты он не обещает: незнакомое слово — UNKNOWN, а не
// FAILED (Р-20). Сверка точная и регистрозависимая: другой регистр или пробел — другой статус.
public final class ProviderOrderStatus {

    public enum ProviderOrderOutcome {
        PAID,
        AUTHORIZED,
        FAILED_FINAL,
        NON_FINAL,
        // Финал от операции мимо портала (реверсал, возврат, закрытие): сколько денег вернулось —
        // неизвестно, поэтому локальный статус не трогается и FAILED не ставится никогда.
        SETTLED_OTHER,
        // Нет в словаре. Сюда же null, пустое и не-строки.
        UNKNOWN
    }

    // §5.8.8: FullyPaid — успешная покупка.
    private static final String FULLY_PAID = "FullyPaid";

    // Cleared (холд списан) и Authorized (холд поставлен) — из старого кода, контрактом не подтверждены:
    // DMS в нём нет (AGENTS.md §10). Не удалять — на них держится DMS-поток.
    private static final String CLEARED = "Cleared";

    private static final String AUTHORIZED = "Authorized";

    // Rejected и Expired — §5.8.8; Failed и Declined — из старого кода, контрактом не подтверждены.
    private static final String REJECTED = "Rejected";
    private static final String EXPIRED = "Expired";
    private static final String FAILED = "Failed";
    private static final String DECLINED = "Declined";

    // §5.1 и §5.8.3 (prevStatus): заказ есть, карту ещё не вводили.
    private static final String PREPARING = "Preparing";

    // §5.8.8: PartPaid — частичный реверсал или возврат, Cancelled — полный реверсал, Refused — полный
    // возврат, Closed — запросы по заказу закрыты. Canceled — написание старого кода, держится рядом.
    private static final String PART_PAID = "PartPaid";
    private static final String CANCELLED = "Cancelled";
    private static final String CANCELED_LEGACY_SPELLING = "Canceled";
    private static final String REFUSED = "Refused";
    private static final String CLOSED = "Closed";

    private static final Map<String, ProviderOrderOutcome> BY_STATUS = Map.ofEntries(
            Map.entry(FULLY_PAID, ProviderOrderOutcome.PAID),
            Map.entry(CLEARED, ProviderOrderOutcome.PAID),
            Map.entry(AUTHORIZED, ProviderOrderOutcome.AUTHORIZED),
            Map.entry(REJECTED, ProviderOrderOutcome.FAILED_FINAL),
            Map.entry(EXPIRED, ProviderOrderOutcome.FAILED_FINAL),
            Map.entry(FAILED, ProviderOrderOutcome.FAILED_FINAL),
            Map.entry(DECLINED, ProviderOrderOutcome.FAILED_FINAL),
            Map.entry(PREPARING, ProviderOrderOutcome.NON_FINAL),
            Map.entry(PART_PAID, ProviderOrderOutcome.SETTLED_OTHER),
            Map.entry(CANCELLED, ProviderOrderOutcome.SETTLED_OTHER),
            Map.entry(CANCELED_LEGACY_SPELLING, ProviderOrderOutcome.SETTLED_OTHER),
            Map.entry(REFUSED, ProviderOrderOutcome.SETTLED_OTHER),
            Map.entry(CLOSED, ProviderOrderOutcome.SETTLED_OTHER)
    );

    private ProviderOrderStatus() {}

    // raw — как пришло с провода, бывает null и не строкой; результат не null никогда.
    public static ProviderOrderOutcome classify(Object raw) {
        if (!(raw instanceof String status)) {
            return ProviderOrderOutcome.UNKNOWN;
        }
        return BY_STATUS.getOrDefault(status, ProviderOrderOutcome.UNKNOWN);
    }
}
