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

    // lastPaidAt и оба счётчика приходят готовыми: лишний запрос на вызов может позволить себе
    // только одиночный эндпоинт, список считает всю страницу разом (P2-15). Чем считать — решает
    // сервис: использования по PAID_STATUSES, возвраты по возвращённой паре (P2-16).
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

    // lastPaidAt передаётся, а не ищется: вызывающий разрешает всю страницу одним группирующим
    // запросом, и этот маппер в таблицу транзакций не ходит вовсе (P2-15).
    public PaymentLinkSummaryResponse toSummary(PaymentLink link, Instant lastPaidAt) {
        return new PaymentLinkSummaryResponse(
                link.getId(),
                link.getStatus(),
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
