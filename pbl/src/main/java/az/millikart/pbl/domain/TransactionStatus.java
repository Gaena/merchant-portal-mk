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

    // Платёж состоялся: деньги ушли с карты. Единственный ответ на «пользовались ли ссылкой» —
    // счётчик, lastPaidAt, слоты, запрет понижать maxPayments. Возвращённый платёж тоже состоялся
    // (Р-49): сузишь набор до SUCCESS — возврат освободит слот, и ссылка с лимитом 3 соберёт 4.
    // AUTHORIZED вне набора намеренно: холд — не взятые деньги, слот он занимает по P1-6.
    public static final Set<TransactionStatus> PAID_STATUSES =
            Collections.unmodifiableSet(EnumSet.of(SUCCESS, REFUNDED, PARTIALLY_REFUNDED));
}
