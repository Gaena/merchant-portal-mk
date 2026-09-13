package az.millikart.pbl.provider;

import java.util.Map;

// Словарь значений order.status эквайера (§5.8.8, v0.1.3). Слово MAJOR в заголовке раздела не
// случайно: полноты списка эквайер не обещает, поэтому незнакомое слово — UNKNOWN, а не FAILED
// (инвариант Р-20: неизвестность не доказательство, что платежа не было). Сверка точная и
// регистрозависимая: лишний пробел или другой регистр — это другой статус, и он должен быть виден.
public final class ProviderOrderStatus {

    // Что статус провайдера значит для локальной транзакции.
    public enum ProviderOrderOutcome {
        PAID,
        AUTHORIZED,
        FAILED_FINAL,
        NON_FINAL,
        // Финал, до которого довела операция не с нашей стороны: реверсал или возврат у эквайера
        // либо закрытие заказа для API. Деньги могли вернуться, но сколько — локально неизвестно,
        // поэтому локальный статус не трогается. Никогда не повод пометить транзакцию FAILED.
        SETTLED_OTHER,
        // Нет в словаре. Сюда же null, пустое и не-строки.
        UNKNOWN
    }

    // §5.8.8: FullyPaid — успешная покупка.
    private static final String FULLY_PAID = "FullyPaid";

    // Cleared (списание холда) и Authorized (холд поставлен) взяты из исходников: контракт
    // описывает только SMS, DMS (Order_DMS) в нём не описан вовсе, и эти статусы им НЕ подтверждены
    // (AGENTS.md §10). Оставлены, чтобы не сломать работающий DMS-поток; ждут подтверждения
    // от MilliKart.
    private static final String CLEARED = "Cleared";

    private static final String AUTHORIZED = "Authorized";

    // §5.8.8: Rejected — ошибочная транзакция, Expired — истекла по времени. Failed и Declined
    // взяты из исходников и контрактом не подтверждены.
    private static final String REJECTED = "Rejected";
    private static final String EXPIRED = "Expired";
    private static final String FAILED = "Failed";
    private static final String DECLINED = "Declined";

    // §5.1 и §5.8.3 (prevStatus): заказ есть, карту ещё не вводили.
    private static final String PREPARING = "Preparing";

    // §5.8.8: PartPaid — частичный реверсал или возврат, Cancelled — полный реверсал, Refused —
    // полный возврат, Closed — по заказу нельзя слать запросы. Canceled с одной l — написание из
    // нашего старого кода; держится рядом, чтобы ни одно из двух не свалилось в UNKNOWN.
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

    // raw — значение под ключом status как оно пришло с провода: может быть null или вовсе не
    // строкой. Результат никогда не null, метод не бросает.
    public static ProviderOrderOutcome classify(Object raw) {
        if (!(raw instanceof String status)) {
            return ProviderOrderOutcome.UNKNOWN;
        }
        return BY_STATUS.getOrDefault(status, ProviderOrderOutcome.UNKNOWN);
    }
}
