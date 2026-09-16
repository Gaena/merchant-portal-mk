package az.millikart.pbl.provider;

import az.millikart.common.exception.BusinessException;

// Шлюз прочитал запрос и отказал (4xx или errorCode): ничего не выполнено, и это не сбой шлюза.
// Circuit breaker и retry `acquiring` его пропускают (application.yaml): иначе один терминал с
// неверным паролем размыкал бы приём платежей всем мерчантам, а повторы утраивали бы отказ.
public class AcquirerDeclinedException extends BusinessException {

    public AcquirerDeclinedException(String message) {
        super(message);
    }
}
