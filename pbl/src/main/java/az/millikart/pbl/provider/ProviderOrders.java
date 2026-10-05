package az.millikart.pbl.provider;

import az.millikart.pbl.domain.CustomerPhone;
import az.millikart.pbl.domain.PaymentLink;
import az.millikart.pbl.domain.PaymentType;
import az.millikart.pbl.domain.UsageType;
import az.millikart.txpg.dto.EcomCreateOrderRequest;
import az.millikart.txpg.dto.NewOrder;

// Ссылка — в заказ провайдера: txpg-client о ссылках не знает (TXPG-CLIENT).
public final class ProviderOrders {

    private static final String DEFAULT_DESCRIPTION = "Payment via Pay-By-Link";

    private ProviderOrders() {
    }

    public static NewOrder of(PaymentLink link) {
        return new NewOrder(
                link.getPaymentType() == PaymentType.DMS,
                link.getAmount(),
                link.getCurrency(),
                link.getDescription() != null ? link.getDescription() : DEFAULT_DESCRIPTION,
                payerOf(link));
    }

    // Клиент для 3DS (Р-96): только у одноразовой ссылки и только заполненные поля. Телефон, который
    // не разбирается как азербайджанский (ссылки до Р-96), не уходит.
    private static EcomCreateOrderRequest.TdsPresetAreq payerOf(PaymentLink link) {
        if (link.getUsageType() != UsageType.SINGLE) {
            return null;
        }
        String name = blankToNull(link.getCustomerName());
        String email = blankToNull(link.getCustomerEmail());
        EcomCreateOrderRequest.Phone phone = CustomerPhone.subscriberOf(link.getCustomerPhone())
                .map(subscriber -> new EcomCreateOrderRequest.Phone(subscriber, CustomerPhone.COUNTRY_CODE))
                .orElse(null);
        if (name == null && email == null && phone == null) {
            return null;
        }
        return new EcomCreateOrderRequest.TdsPresetAreq(name, email, phone);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
