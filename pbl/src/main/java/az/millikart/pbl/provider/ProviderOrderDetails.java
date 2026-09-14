package az.millikart.pbl.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Маскированная карта, RRN и approvalCode из order-payload эквайера — то, чем мерчант сверяет
// платёж с выпиской (§5.8.3-5.8.6). Ни одного из трёх нет на верхнем уровне order, где их искал
// прежний код (P1-16). Ничего здесь не бросает: это путь чтения карточки транзакции, он не вправе
// падать из-за формы чужого payload. Выбор записи покупки — см. purchaseRecord.
public final class ProviderOrderDetails {

    private static final Logger log = LoggerFactory.getLogger(ProviderOrderDetails.class);

    // Любой из трёх фактов может быть null: payload бывает старше платежа, платёж мог упасть до
    // ввода карты, эквайер мог просто не прислать поле.
    public record TransactionFacts(String maskedCard, String rrn, String approvalCode) {

        static final TransactionFacts EMPTY = new TransactionFacts(null, null, null);
    }

    // §5.8.8: описания записей order.trans[]. Void ищется подстрокой — в контракте слово
    // встречается один раз (Purchase - Void), но отмена чего угодно не является покупкой.
    static final String DESCRIPTION_PURCHASE = "Purchase";

    static final String DESCRIPTION_REFUND = "Refund";

    static final String DESCRIPTION_VOID_MARKER = "Void";

    private ProviderOrderDetails() {
    }

    // orderPayload — объект order из ответа getOrderStatus (он же лежит в provider_response), может
    // быть null. Результат сам никогда не null и не бросает.
    public static TransactionFacts read(Map<String, Object> orderPayload) {
        if (orderPayload == null) {
            return TransactionFacts.EMPTY;
        }
        String maskedCard = maskedCard(orderPayload);

        Map<String, Object> record = purchaseRecord(orderPayload);
        if (record == null) {
            // Под isDebugEnabled: это выполняется на каждую строку списка транзакций, а копии,
            // которые делает forLog, стоит делать только если их кто-то прочтёт.
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

    // §5.8.4: order.srcToken.displayName как есть — последние четыре цифры отрезает фронтенд.
    private static String maskedCard(Map<String, Object> orderPayload) {
        Map<String, Object> srcToken = asMap(orderPayload.get("srcToken"));
        return srcToken != null ? ProviderPayloads.scalarText(srcToken.get("displayName")) : null;
    }

    // Запись, с которой читаются rrn и approvalCode: покупка из order.trans[] (§5.8.5-5.8.6), иначе
    // lastTran, иначе null. Мерчанту нужны идентификаторы именно покупки — той операции, которую
    // показывает выписка плательщика.
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

    // Кандидат: не реверсал, не возврат, не отмена.
    private static boolean isPurchaseCandidate(Map<String, Object> record) {
        if (isTrue(record.get("isReversal"))) {
            return false;
        }
        String description = ProviderPayloads.scalarText(record.get("description"));
        if (description == null) {
            // Совсем без description — случай DMS, который контракт не описывает. Оставляем.
            return true;
        }
        return !DESCRIPTION_REFUND.equals(description) && !description.contains(DESCRIPTION_VOID_MARKER);
    }

    // description=Purchase — предпочтение, а не строгий фильтр, намеренно: DMS (Order_DMS) контракт
    // не описывает вовсе (AGENTS.md §10), и какой description несут авторизация и клиринг —
    // неизвестно. Строгий фильтр оставил бы каждый DMS-платёж без RRN.
    private static List<Map<String, Object>> preferPurchase(List<Map<String, Object>> candidates) {
        List<Map<String, Object>> purchases = new ArrayList<>();
        for (Map<String, Object> record : candidates) {
            if (DESCRIPTION_PURCHASE.equals(ProviderPayloads.scalarText(record.get("description")))) {
                purchases.add(record);
            }
        }
        return purchases.isEmpty() ? candidates : purchases;
    }

    // Самая ранняя по regTime. Строки вида "2023-03-14 10:30:39" сравниваются как текст намеренно —
    // не «чинить» это через LocalDateTime.parse: неожиданный формат тогда бросит посреди отрисовки
    // карточки транзакции, а сравнение строк на странном значении — всего лишь неверный выбор.
    // Запись с regTime бьёт запись без него; среди безымянных остаётся первая по порядку списка.
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

    // По контракту isReversal — JSON-boolean, но шлюз, написавший строку "true", имеет в виду
    // реверсал. Всё прочее — отсутствие, false, число, структура — «не реверсал».
    private static boolean isTrue(Object value) {
        return Boolean.TRUE.equals(value) || (value instanceof String s && s.trim().equalsIgnoreCase("true"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    // Payload эквайера попадает в лог только через ProviderPayloads.withoutSecrets (P0-9). trans[]
    // и lastTran по контракту пароля не несут (§5.8.3), но правило есть правило, а цена — одна
    // копия map на DEBUG-пути, который и так заканчивается «ничего не нашли».
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
