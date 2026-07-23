package az.millikart.pbl.provider;

import az.millikart.pbl.domain.PaymentLink;
import az.millikart.pbl.provider.dto.EcomCreateOrderResponse;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public interface AcquiringClient {
    EcomCreateOrderResponse createEcomOrder(PaymentLink link, String login, String password, UUID merchantRid, String hppRedirectUrl);
    Map<String, Object> completeDms(String providerOrderId, String password, String login, String terminalPassword, BigDecimal amount);
    Map<String, Object> refund(String providerOrderId, String password, String login, String terminalPassword, BigDecimal amount);
    Map<String, Object> getOrderStatus(String providerOrderId, String password, String login, String terminalPassword);
}
