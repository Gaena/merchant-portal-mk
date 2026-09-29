package az.millikart.common.web;

// Адрес клиента текущего запроса: кладёт ClientIpFilter, читает прежде всего журнал аудита — чтобы
// адрес не протаскивался через каждую сигнатуру (Р-36).
public final class ClientIpHolder {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private ClientIpHolder() {
    }

    public static void set(String clientIp) {
        CURRENT.set(clientIp);
    }

    // Вне запроса (планировщик, тест) — null: клиента нет, это не ошибка.
    public static String get() {
        return CURRENT.get();
    }

    // Только в finally ClientIpFilter: поток из пула унёс бы значение в следующий запрос (Р-36).
    public static void clear() {
        CURRENT.remove();
    }
}
