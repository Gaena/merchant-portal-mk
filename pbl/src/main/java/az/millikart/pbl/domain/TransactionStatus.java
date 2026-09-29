package az.millikart.pbl.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum TransactionStatus {
    PENDING,
    AUTHORIZED,
    SUCCESS,
    FAILED,
    PARTIALLY_REFUNDED,
    REFUNDED;

    // Единственный ответ на «платёж состоялся»: счётчик, lastPaidAt, слоты, maxPayments (Р-49). Не
    // сужать до SUCCESS: возврат освободит слот, и ссылка с лимитом 3 соберёт 4. AUTHORIZED вне набора
    // намеренно — холд не взятые деньги, слот он занимает по P1-6.
    public static final Set<TransactionStatus> PAID_STATUSES =
            Collections.unmodifiableSet(EnumSet.of(SUCCESS, REFUNDED, PARTIALLY_REFUNDED));
}
