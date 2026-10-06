package az.millikart.pbl.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Название и VÖEN платёжного провайдера на чеке плательщика — закон о платёжных услугах, ст. 17.1.1
// (Р-130). Без умолчаний и обязательны: чек без них закону не соответствует, поэтому пустое или кривое
// значение — отказ старта, а не чек без строки.
@Component
public class ReceiptRequisites {

    static final String NAME_VARIABLE = "RECEIPT_PROVIDER_NAME";
    static final String TAX_ID_VARIABLE = "RECEIPT_PROVIDER_TAX_ID";

    private static final String HOW_TO_FIX = """
            How to fix:
              Export the payment service provider's legal name and VÖEN before starting pbl, for example:
                export RECEIPT_PROVIDER_NAME='MilliKart LLC'
                export RECEIPT_PROVIDER_TAX_ID='1234567890'
              Both are printed on every payer receipt (payment services law, art. 17.1.1) and come from
              the customer. See .env.example and project_docs/guides/deployment_guide.md, section 20.1.""";

    private final String providerName;
    private final String providerTaxId;

    public ReceiptRequisites(@Value("${pbl.receipt.provider-name}") String providerName,
                             @Value("${pbl.receipt.provider-tax-id}") String providerTaxId) {
        if (providerName == null || providerName.isBlank()) {
            throw new IllegalStateException("The environment variable " + NAME_VARIABLE
                    + " (property pbl.receipt.provider-name) is empty. The service cannot start without it.\n"
                    + HOW_TO_FIX);
        }
        if (providerTaxId == null || !providerTaxId.matches("\\d{10}")) {
            throw new IllegalStateException("The environment variable " + TAX_ID_VARIABLE
                    + " (property pbl.receipt.provider-tax-id) must be a VÖEN of exactly 10 digits.\n"
                    + HOW_TO_FIX);
        }
        this.providerName = providerName.strip();
        this.providerTaxId = providerTaxId;
    }

    public String providerName() {
        return providerName;
    }

    public String providerTaxId() {
        return providerTaxId;
    }
}
