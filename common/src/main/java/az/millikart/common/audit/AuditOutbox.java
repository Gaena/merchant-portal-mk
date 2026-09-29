package az.millikart.common.audit;

import java.util.ArrayList;
import java.util.List;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Записи журнала, отложенные до конца HTTP-запроса (Р-85): своя транзакция посреди запроса берёт
// второе соединение из пула, и одновременные отказы выбирают пул и ждут друг друга, теряя записи.
final class AuditOutbox {

    private static final ThreadLocal<List<Runnable>> PENDING = new ThreadLocal<>();

    private AuditOutbox() {
    }

    static void open() {
        PENDING.set(new ArrayList<>());
    }

    // true — запись отложена до конца запроса. Вне запроса (планировщик, прямой вызов в тесте) или
    // вне транзакции второго соединения никто не держит, и писать можно сразу.
    static boolean defer(Runnable write) {
        List<Runnable> pending = PENDING.get();
        if (pending == null || !TransactionSynchronizationManager.isActualTransactionActive()) {
            return false;
        }
        pending.add(write);
        return true;
    }

    static List<Runnable> close() {
        List<Runnable> pending = PENDING.get();
        PENDING.remove();
        return pending != null ? pending : List.of();
    }
}
