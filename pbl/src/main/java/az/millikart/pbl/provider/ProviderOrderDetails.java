package az.millikart.pbl.provider;

import az.millikart.txpg.ProviderPayloads;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Карта, RRN и approvalCode из order эквайера (§5.8.3-5.8.6): на верхнем уровне order их нет (P1-16).
// Ничего здесь не бросает: карточка транзакции не вправе падать из-за формы чужого payload.
public final class ProviderOrderDetails {

    private static final Logger log = LoggerFactory.getLogger(ProviderOrderDetails.class);

    // Любое поле бывает null: payload старше платежа, платёж упал до ввода карты, эквайер не прислал поле.
    public record TransactionFacts(String maskedCard, String rrn, String approvalCode) {

        static final TransactionFacts EMPTY = new TransactionFacts(null, null, null);
    }

    // §5.8.8: описания записей order.trans[]. Void — подстрокой: в контракте есть «Purchase - Void»,
    // а отмена чего угодно — не покупка.
    static final String DESCRIPTION_PURCHASE = "Purchase";

    static final String DESCRIPTION_REFUND = "Refund";

    static final String DESCRIPTION_VOID_MARKER = "Void";

    private ProviderOrderDetails() {
    }

    // orderPayload — order из getOrderStatus или provider_response, бывает null; результат — никогда.
    public static TransactionFacts read(Map<String, Object> orderPayload) {
        if (orderPayload == null) {
            return TransactionFacts.EMPTY;
        }
        String maskedCard = maskedCard(orderPayload);

        Map<String, Object> record = purchaseRecord(orderPayload);
        if (record == null) {
            // isDebugEnabled: вызов идёт на каждую строку списка, а копии forLog нужны, только если их прочтут.
            if (log.isDebugEnabled()) {
                log.debug("Order payload carries no usable card operation record (order id {}): trans={}, lastTran={}",
                        orderPayload.get("id"), forLog(orderPayload.get("trans")), forLog(orderPayload.get("lastTran")));
            }
            return new TransactionFacts(maskedCard, null, null);
        }
        return new TransactionFacts(
                maskedCard,
                ProviderPayloads.scalarText(record.get("rrn")),
                ProviderPayloads.scalarText(record.get("approvalCode")));
    }

    // Плательщик отправил карту: в order.trans[] есть запись операции — оплата, отказ банка, оборванный
    // 3-D Secure (Р-128). Список приходит при tranDetailLevel=2, его шлёт каждый опрос; брошенный до ввода
    // карты заказ записей не несёт. Тот же признак размечает старые строки (миграция pbl/016).
    public static boolean hasCardOperation(Map<String, Object> orderPayload) {
        return orderPayload != null
                && orderPayload.get("trans") instanceof List<?> trans
                && !trans.isEmpty();
    }

    // Closed после Authorized без списаний — холд снял банк (Р-75). Списание — положительный clearAmount
    // (§5.8.8), у авторизации он 0. Нет списка, пустой или нечитаемая сумма — «не доказано», ручной разбор.
    public static boolean isReleasedAuthorization(Map<String, Object> orderPayload) {
        if (orderPayload == null
                || !"Closed".equals(ProviderPayloads.scalarText(orderPayload.get("status")))
                || !"Authorized".equals(ProviderPayloads.scalarText(orderPayload.get("prevStatus")))
                || !(orderPayload.get("trans") instanceof List<?> trans)
                || trans.isEmpty()) {
            return false;
        }
        for (Object element : trans) {
            Map<String, Object> record = asMap(element);
            BigDecimal cleared = record != null ? decimal(record.get("clearAmount")) : null;
            if (cleared == null || cleared.signum() > 0) {
                return false;
            }
        }
        return true;
    }

    private static BigDecimal decimal(Object value) {
        String text = ProviderPayloads.scalarText(value);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // §5.8.4: order.srcToken.displayName как есть — последние четыре цифры отрезает фронтенд.
    private static String maskedCard(Map<String, Object> orderPayload) {
        Map<String, Object> srcToken = asMap(orderPayload.get("srcToken"));
        return srcToken != null ? ProviderPayloads.scalarText(srcToken.get("displayName")) : null;
    }

    // Покупка из order.trans[] (§5.8.5-5.8.6), иначе lastTran: мерчанту нужны rrn и approvalCode именно
    // покупки — операции из выписки плательщика.
    private static Map<String, Object> purchaseRecord(Map<String, Object> orderPayload) {
        List<Map<String, Object>> candidates = new ArrayList<>();
        if (orderPayload.get("trans") instanceof List<?> trans) {
            for (Object element : trans) {
                Map<String, Object> record = asMap(element);
                if (record != null && isPurchaseCandidate(record)) {
                    candidates.add(record);
                }
            }
        }
        Map<String, Object> chosen = earliest(preferPurchase(candidates));
        if (chosen != null) {
            return chosen;
        }
        // §5.8.3: при одном orderDetailLevel=2, без tranDetailLevel=2, заказ несёт lastTran вместо
        // списка. Фильтры те же: последняя операция возвращённого заказа — возврат, а не покупка.
        Map<String, Object> lastTran = asMap(orderPayload.get("lastTran"));
        return lastTran != null && isPurchaseCandidate(lastTran) ? lastTran : null;
    }

    private static boolean isPurchaseCandidate(Map<String, Object> record) {
        if (isTrue(record.get("isReversal"))) {
            return false;
        }
        String description = ProviderPayloads.scalarText(record.get("description"));
        if (description == null) {
            // Без description бывает DMS, которого нет в контракте, — остаётся кандидатом.
            return true;
        }
        return !DESCRIPTION_REFUND.equals(description) && !description.contains(DESCRIPTION_VOID_MARKER);
    }

    // Purchase — предпочтение, а не фильтр: description записей DMS неизвестен (AGENTS.md §10), и строгий
    // фильтр оставил бы каждый DMS-платёж без RRN.
    private static List<Map<String, Object>> preferPurchase(List<Map<String, Object>> candidates) {
        List<Map<String, Object>> purchases = new ArrayList<>();
        for (Map<String, Object> record : candidates) {
            if (DESCRIPTION_PURCHASE.equals(ProviderPayloads.scalarText(record.get("description")))) {
                purchases.add(record);
            }
        }
        return purchases.isEmpty() ? candidates : purchases;
    }

    // regTime ("2023-03-14 10:30:39") сравнивается строкой — не парсить (P1-16): неожиданный формат
    // бросит посреди карточки, а строка на нём даст лишь неверный выбор. Запись с regTime бьёт запись
    // без него; среди остальных — первая по списку.
    private static Map<String, Object> earliest(List<Map<String, Object>> records) {
        Map<String, Object> best = null;
        String bestTime = null;
        for (Map<String, Object> record : records) {
            String time = ProviderPayloads.scalarText(record.get("regTime"));
            if (best == null) {
                best = record;
                bestTime = time;
                continue;
            }
            if (time != null && (bestTime == null || time.compareTo(bestTime) < 0)) {
                best = record;
                bestTime = time;
            }
        }
        return best;
    }

    // По контракту boolean, но строка "true" — тоже реверсал; всё прочее — нет.
    private static boolean isTrue(Object value) {
        return Boolean.TRUE.equals(value) || (value instanceof String s && s.trim().equalsIgnoreCase("true"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    // Payload в лог — только через withoutSecrets (P0-9), даже trans[] и lastTran, где пароля по
    // контракту нет (§5.8.3).
    private static Object forLog(Object value) {
        if (value instanceof Map<?, ?>) {
            return ProviderPayloads.withoutSecrets(asMap(value));
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object element : list) {
                copy.add(forLog(element));
            }
            return copy;
        }
        return value;
    }
}
