package az.millikart.pbl.service;

import az.millikart.pbl.domain.PaymentLink;
import az.millikart.pbl.dto.CustomerDto;
import az.millikart.pbl.dto.PaymentLinkResponse;
import az.millikart.pbl.dto.PaymentLinkSummaryResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Maps {@link PaymentLink} entities to their API response representations.
 */
@Component
public class PaymentLinkMapper {

    private final String baseUrl;

    public PaymentLinkMapper(@Value("${pbl.base-url}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Builds the customer-facing open URL for a payment link.
     */
    public String buildOpenLink(PaymentLink link) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .path("/api/v1/payment-links/{id}/open")
                .build(link.getId())
                .toString();
    }

    public PaymentLinkResponse toResponse(PaymentLink link, int currentPaymentsCount) {
        CustomerDto customer = null;
        if (link.getCustomerName() != null || link.getCustomerEmail() != null || link.getCustomerPhone() != null) {
            customer = new CustomerDto(link.getCustomerName(), link.getCustomerEmail(), link.getCustomerPhone());
        }
        return new PaymentLinkResponse(
                link.getId(),
                link.getProviderReference(),
                link.getMerchantOrderId(),
                link.getTerminalId(),
                link.getAmount(),
                link.getCurrency(),
                link.getDescription(),
                customer,
                link.getPaymentType(),
                link.getUsageType(),
                link.getMaxPayments(),
                currentPaymentsCount,
                link.getStatus(),
                buildOpenLink(link),
                link.getMetadata(),
                link.getCreatedAt()
        );
    }

    public PaymentLinkSummaryResponse toSummary(PaymentLink link) {
        return new PaymentLinkSummaryResponse(
                link.getId(),
                link.getStatus(),
                link.getAmount(),
                link.getCurrency(),
                link.getCreatedAt()
        );
    }
}
