package az.millikart.pbl;

import az.millikart.pbl.config.ReceiptRequisites;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

// Р-130: реквизиты провайдера печатаются на каждом чеке по закону (ст. 17.1.1), поэтому пустые или кривые
// — отказ старта, а не чек без строки. Чистый JUnit: проверка целиком в конструкторе.
class ReceiptRequisitesTest {

    @Test
    void validRequisites_areKeptWithoutSurroundingSpaces() {
        ReceiptRequisites requisites = new ReceiptRequisites(" MilliKart LLC ", "1234567890");
        Assertions.assertEquals("MilliKart LLC", requisites.providerName());
        Assertions.assertEquals("1234567890", requisites.providerTaxId());
    }

    @Test
    void blankProviderName_stopsTheService() {
        IllegalStateException e = Assertions.assertThrows(IllegalStateException.class,
                () -> new ReceiptRequisites(" ", "1234567890"));
        Assertions.assertTrue(e.getMessage().contains("RECEIPT_PROVIDER_NAME"));
    }

    @Test
    void taxIdThatIsNotTenDigits_stopsTheService() {
        for (String taxId : new String[] {null, "", "123456789", "12345678901", "12345678ab", " 1234567890"}) {
            IllegalStateException e = Assertions.assertThrows(IllegalStateException.class,
                    () -> new ReceiptRequisites("MilliKart LLC", taxId), String.valueOf(taxId));
            Assertions.assertTrue(e.getMessage().contains("RECEIPT_PROVIDER_TAX_ID"));
        }
    }
}
