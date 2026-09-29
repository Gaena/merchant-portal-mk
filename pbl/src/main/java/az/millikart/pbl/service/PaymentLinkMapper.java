package az.millikart.pbl.service;

import az.millikart.pbl.domain.PaymentLink;
import az.millikart.pbl.dto.CustomerDto;
import az.millikart.pbl.dto.PaymentLinkResponse;
import az.millikart.pbl.dto.PaymentLinkSummaryResponse;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class PaymentLinkMapper {

    private final String baseUrl;

    public PaymentLinkMapper(@Value("${pbl.base-url}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String buildOpenLink(PaymentLink link) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .path("/api/v1/payment-links/{id}/open")
                .build(link.getId())
                .toString();
    }

    // Счётчики и lastPaidAt считает сервис (P2-16): маппер в базу не ходит, иначе список — N+1 (P2-15).
    public PaymentLinkResponse toResponse(PaymentLink link, int currentPaymentsCount,
                                          int refundedPaymentsCount, Instant lastPaidAt) {
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
                refundedPaymentsCount,
                link.getStatus(),
                buildOpenLink(link),
                link.getMetadata(),
                link.getExpiresAt(),
                lastPaidAt,
                link.getCreatedAt()
        );
    }

    public PaymentLinkSummaryResponse toSummary(PaymentLink link, Instant lastPaidAt) {
        return new PaymentLinkSummaryResponse(
                link.getId(),
                link.getStatus(),
                link.getTerminalId(),
                link.getAmount(),
                link.getCurrency(),
                link.getDescription(),
                link.getCustomerName(),
                link.getCustomerEmail(),
                link.getCustomerPhone(),
                link.getPaymentType(),
                link.getUsageType(),
                link.getMaxPayments(),
                link.getExpiresAt(),
                lastPaidAt,
                link.getCreatedAt()
        );
    }
}
