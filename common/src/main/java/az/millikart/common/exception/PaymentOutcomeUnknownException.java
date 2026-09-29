package az.millikart.common.exception;

// Деньги ушли эквайеру, подтверждения нет (таймаут, обрыв, 5xx): ни менять локальное состояние, ни
// повторять вслепую нельзя. Не BusinessException намеренно: 400 читается как «отказ, повтори», а
// повторный возврат — двойной возврат.
public class PaymentOutcomeUnknownException extends RuntimeException {

    public PaymentOutcomeUnknownException(String message) {
        super(message);
    }

    public PaymentOutcomeUnknownException(String message, Throwable cause) {
        super(message, cause);
    }
}
