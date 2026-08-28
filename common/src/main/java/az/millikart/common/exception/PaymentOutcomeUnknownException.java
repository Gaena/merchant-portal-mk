package az.millikart.common.exception;

// Движение денег ушло эквайеру, но подтверждение не вернулось: таймаут чтения, оборванное
// соединение, 5xx. Могло пройти, могло нет — ни менять локальное состояние, ни повторять вызов
// вслепую нельзя. Намеренно не BusinessException: то читается мерчантом как «шлюз посмотрел и
// отказал, ничего не произошло, повтори», а повторный возврат — это возврат дважды. Отсюда 502.
public class PaymentOutcomeUnknownException extends RuntimeException {

    public PaymentOutcomeUnknownException(String message) {
        super(message);
    }

    public PaymentOutcomeUnknownException(String message, Throwable cause) {
        super(message, cause);
    }
}
