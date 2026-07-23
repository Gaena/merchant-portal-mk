package az.millikart.pbl.provider;

import az.millikart.pbl.domain.PaymentLink;
import az.millikart.pbl.provider.dto.EcomCreateOrderResponse;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "pbl.provider.stub", havingValue = "true")
public class StubAcquiringClient implements AcquiringClient {

    @Override
    public EcomCreateOrderResponse createEcomOrder(PaymentLink link, String login, String password, UUID merchantRid, String hppRedirectUrl) {
        long orderId = (long) (Math.random() * 1000000000L);
        String hppUrl = "https://gateway.txpg.example.com/pay?rid=" + orderId;
        return new EcomCreateOrderResponse(
                new EcomCreateOrderResponse.Order(hppUrl, orderId, "Preparing", "password123")
        );
    }

    @Override
    public Map<String, Object> completeDms(String providerOrderId, String password, String login, String terminalPassword, BigDecimal amount) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("providerOrderId", providerOrderId);
        response.put("amount", amount);
        return response;
    }

    @Override
    public Map<String, Object> refund(String providerOrderId, String password, String login, String terminalPassword, BigDecimal amount) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "REFUNDED");
        response.put("refundId", "REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        return response;
    }

    @Override
    public Map<String, Object> getOrderStatus(String providerOrderId, String password, String login, String terminalPassword) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "FullyPaid");
        response.put("id", providerOrderId);
        return response;
    }
}
