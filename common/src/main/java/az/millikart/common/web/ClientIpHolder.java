package az.millikart.common.web;

// Адрес клиента текущего запроса на этом потоке: кладёт ClientIpFilter, читают там, где важно
// происхождение запроса, — прежде всего журнал аудита в directory. Существует ради того, чтобы
// адрес не протаскивался через каждую сигнатуру отдельным параметром или HttpServletRequest
// (Р-36): он едет рядом с вызовом, а не через весь путь до него.
public final class ClientIpHolder {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private ClientIpHolder() {
    }

    public static void set(String clientIp) {
        CURRENT.set(clientIp);
    }

    // Вне запроса — планировщики, старт приложения, прямые вызовы из тестов — здесь null, и это
    // нормальный ответ «клиента нет», а не ошибка.
    public static String get() {
        return CURRENT.get();
    }

    // Обязан вызываться при завершении запроса, в finally: потоки сервлетов берутся из пула, и
    // оставленное значение уедет в тот запрос, который поток подхватит следующим (Р-36).
    // Единственный писатель и владелец этой очистки — ClientIpFilter.
    public static void clear() {
        CURRENT.remove();
    }
}
