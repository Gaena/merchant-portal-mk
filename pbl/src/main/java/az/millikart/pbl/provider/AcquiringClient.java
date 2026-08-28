package az.millikart.pbl.provider;

import az.millikart.pbl.domain.PaymentLink;
import az.millikart.pbl.provider.dto.EcomCreateOrderResponse;
import az.millikart.pbl.provider.dto.MoneyOperationResult;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public interface AcquiringClient {
    EcomCreateOrderResponse createEcomOrder(PaymentLink link, String login, String password, UUID merchantRid, String hppRedirectUrl);

    // Обе денежные операции возвращают результат, только если эквайер подтвердил её через
    // tran.match.ridByPmo; принятый, но неподтверждённый ответ — PaymentOutcomeUnknownException,
    // но никогда не результат (P1-8b).
    MoneyOperationResult completeDms(String providerOrderId, String password, String login, String terminalPassword, BigDecimal amount);

    MoneyOperationResult refund(String providerOrderId, String password, String login, String terminalPassword, BigDecimal amount);

    Map<String, Object> getOrderStatus(String providerOrderId, String password, String login, String terminalPassword);
}
