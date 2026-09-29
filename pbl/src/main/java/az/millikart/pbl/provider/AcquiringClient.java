package az.millikart.pbl.provider;

import az.millikart.pbl.domain.PaymentLink;
import az.millikart.pbl.provider.dto.EcomCreateOrderResponse;
import az.millikart.pbl.provider.dto.MoneyOperationResult;
import az.millikart.pbl.provider.dto.TerminalCheckResult;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public interface AcquiringClient {
    // Все вызовы — с кредами компании терминала, а не терминала (Р-93).
    // Заказ создаётся на терминале провайдера: POST /order?terminalRid=… (Р-96).
    EcomCreateOrderResponse createEcomOrder(PaymentLink link, ProviderCredentials credentials, String terminalRid,
                                            UUID ridByMerchant, String hppRedirectUrl);

    // Денежные операции возвращают результат, только если в ответе есть tran.match.ridByPmo; без него —
    // PaymentOutcomeUnknownException (P1-8b).
    MoneyOperationResult completeDms(String providerOrderId, String password, ProviderCredentials credentials, BigDecimal amount);

    MoneyOperationResult refund(String providerOrderId, String password, ProviderCredentials credentials, BigDecimal amount);

    Map<String, Object> getOrderStatus(String providerOrderId, String password, ProviderCredentials credentials);

    // Пробный заказ: только он проверяет разом креды и право принимать оплаты (Р-70, Р-93). Никогда
    // не бросает — любой исход показывается администратору.
    TerminalCheckResult checkOrderCreation(ProviderCredentials credentials, String terminalRid);
}
