package az.millikart.pbl.provider;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// Причина отказа из order.custAttrs (§5.8.7). Порядок свой (Р-24): описание важнее голого кода.
// Разбор защитный: неожиданная форма даёт Optional.empty(), а не исключение.
public final class ProviderDeclineReason {

    // §5.8.7: отказ e-commerce модуля.
    static final String DECLINE_DESCRIPTION = "DeclineDescription";

    // §5.8.7: описание и код отказа ядра ПЦ.
    static final String PMO_DECLINE_DESCRIPTION = "PmoDeclineDescription";

    static final String PMO_RESULT_CODE = "PmoResultCode";

    // §5.8.3: его несёт и успешный заказ — в «причину отказа» не пускать.
    static final String PMO_RESULT_CODE_APPROVED = "Approved";

    private ProviderDeclineReason() {
    }

    public static Optional<String> extract(Map<String, Object> orderDetails) {
        if (orderDetails == null) {
            return Optional.empty();
        }
        Object custAttrs = orderDetails.get("custAttrs");
        if (!(custAttrs instanceof List<?> attrs)) {
            return Optional.empty();
        }
        Optional<String> declineDescription = valueOf(attrs, DECLINE_DESCRIPTION);
        if (declineDescription.isPresent()) {
            return declineDescription;
        }
        Optional<String> pmoDeclineDescription = valueOf(attrs, PMO_DECLINE_DESCRIPTION);
        if (pmoDeclineDescription.isPresent()) {
            return pmoDeclineDescription;
        }
        return valueOf(attrs, PMO_RESULT_CODE)
                .filter(code -> !PMO_RESULT_CODE_APPROVED.equals(code));
    }

    private static Optional<String> valueOf(List<?> attrs, String rid) {
        for (Object element : attrs) {
            if (!(element instanceof Map<?, ?> attr)) {
                continue;
            }
            if (!rid.equals(attr.get("rid"))) {
                continue;
            }
            Object value = attr.get("valAsStr");
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value);
            if (!text.isBlank()) {
                return Optional.of(text);
            }
        }
        return Optional.empty();
    }
}
