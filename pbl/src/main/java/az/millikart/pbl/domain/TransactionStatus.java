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

    // Слот ссылки: PAID_STATUSES и AUTHORIZED — будущий платёж (P1-6). Им считают открытие и нижнюю границу
    // maxPayments (MAXPAY-HOLDS); выводится из PAID_STATUSES, а не перечисляется заново: разъедутся наборы —
    // одноразовую ссылку с живым холдом откроют повторно, а лимит опустят ниже холда.
    public static final Set<TransactionStatus> SLOT_OCCUPYING_STATUSES = slotOccupying();

    private static Set<TransactionStatus> slotOccupying() {
        EnumSet<TransactionStatus> statuses = EnumSet.copyOf(PAID_STATUSES);
        statuses.add(AUTHORIZED);
        return Collections.unmodifiableSet(statuses);
    }
}
