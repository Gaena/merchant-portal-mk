package az.millikart.pbl.provider;

import az.millikart.common.exception.BusinessException;

// Шлюз отказал (4xx или errorCode): ничего не выполнено, и это не сбой шлюза. Breaker и retry
// `acquiring` его пропускают (application.yaml): иначе одна компания с неверным паролем размыкала
// бы приём платежей всем, а повторы утраивали бы отказ.
public class AcquirerDeclinedException extends BusinessException {

    public AcquirerDeclinedException(String message) {
        super(message);
    }
}
