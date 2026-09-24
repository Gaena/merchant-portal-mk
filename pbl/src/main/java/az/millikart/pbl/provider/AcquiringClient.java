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
    EcomCreateOrderResponse createEcomOrder(PaymentLink link, ProviderCredentials credentials, UUID ridByMerchant, String hppRedirectUrl);

    // Обе денежные операции возвращают результат, только если эквайер подтвердил её через
    // tran.match.ridByPmo; принятый, но неподтверждённый ответ — PaymentOutcomeUnknownException,
    // но никогда не результат (P1-8b).
    MoneyOperationResult completeDms(String providerOrderId, String password, ProviderCredentials credentials, BigDecimal amount);

    MoneyOperationResult refund(String providerOrderId, String password, ProviderCredentials credentials, BigDecimal amount);

    Map<String, Object> getOrderStatus(String providerOrderId, String password, ProviderCredentials credentials);

    /**
     * Проверяет, можно ли создать платёж: пробный заказ с кредами компании терминала (Р-93).
     *
     * Единственный вызов у провайдера, который проверяет разом и логин с паролем, и то, что
     * оплаты разрешены, — это заведение заказа. Запрос статуса проверяет только первое.
     * Пробный заказ остаётся у провайдера неоплаченным и через десять минут уходит в Expired;
     * в выписку такие не попадают, и на такую нагрузку провайдер дал согласие.
     *
     * Никогда не бросает: любой исход — это результат, который надо показать администратору.
     */
    TerminalCheckResult checkOrderCreation(ProviderCredentials credentials);
}
