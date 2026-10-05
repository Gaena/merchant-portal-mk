package az.millikart.pbl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import az.millikart.pbl.domain.PaymentLink;
import az.millikart.pbl.domain.PaymentType;
import az.millikart.pbl.domain.UsageType;
import az.millikart.pbl.provider.ProviderOrders;
import az.millikart.txpg.dto.EcomCreateOrderRequest;
import az.millikart.txpg.dto.NewOrder;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

// Ссылка → заказ провайдера (TXPG-CLIENT): клиент провайдера о ссылках не знает, и правила Р-96 — кого
// из клиентов ссылки отправлять в tdsPresetAreq — живут здесь. Как заказ уходит на провод —
// TxpgAcquiringClientTest в txpg-client.
class ProviderOrdersTest {

    @Test
    void aSingleUseLink_sendsItsCustomerAsThePayer() {
        NewOrder order = ProviderOrders.of(single()
                .customerName("Test Testov").customerEmail("test@test.az").customerPhone("+994703301025").build());

        assertEquals(new EcomCreateOrderRequest.TdsPresetAreq("Test Testov", "test@test.az",
                new EcomCreateOrderRequest.Phone("703301025", "994")), order.payer());
    }

    // У многоразовой ссылки клиента нет, даже если он остался в базе с прошлых времён.
    @Test
    void aMultiUseLink_hasNoPayer() {
        NewOrder order = ProviderOrders.of(link().usageType(UsageType.MULTIPLE)
                .customerName("Legacy Name").customerEmail("legacy@test.az").build());

        assertNull(order.payer());
    }

    // Телефон, который не разбирается как азербайджанский (ссылки до Р-96), не уходит; пустое поле — тоже.
    // Без полей блока нет вовсе.
    @Test
    void unusableFields_areNotSent_andWithoutAnyThereIsNoPayer() {
        assertNull(ProviderOrders.of(single().customerPhone("call me after six").customerName("  ").build()).payer());

        NewOrder order = ProviderOrders.of(single().customerName(" ").customerEmail(" shop@test.az ").build());
        assertEquals(new EcomCreateOrderRequest.TdsPresetAreq(null, "shop@test.az", null), order.payer());
    }

    @Test
    void typeAmountAndDescription_comeFromTheLink() {
        NewOrder dms = ProviderOrders.of(single().paymentType(PaymentType.DMS).description("Invoice 12").build());
        assertTrue(dms.dms());
        assertEquals(new BigDecimal("5.00"), dms.amount());
        assertEquals("AZN", dms.currency());
        assertEquals("Invoice 12", dms.description());

        NewOrder sms = ProviderOrders.of(single().build());
        assertFalse(sms.dms());
        assertEquals("Payment via Pay-By-Link", sms.description(), "a link without a description still describes the order");
    }

    private static PaymentLink.PaymentLinkBuilder link() {
        return PaymentLink.builder().paymentType(PaymentType.SMS).amount(new BigDecimal("5.00")).currency("AZN");
    }

    private static PaymentLink.PaymentLinkBuilder single() {
        return link().usageType(UsageType.SINGLE);
    }
}
