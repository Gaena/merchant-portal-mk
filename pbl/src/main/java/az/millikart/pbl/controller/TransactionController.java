package az.millikart.pbl.controller;

import az.millikart.common.security.UserPrincipal;
import az.millikart.pbl.dto.CompleteDmsRequest;
import az.millikart.pbl.dto.PaymentLinkResponse;
import az.millikart.pbl.dto.RefundRequest;
import az.millikart.pbl.dto.RefundResponse;
import az.millikart.pbl.dto.TransactionResponse;
import az.millikart.pbl.service.PaymentLinkService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final PaymentLinkService paymentLinkService;

    public TransactionController(PaymentLinkService paymentLinkService) {
        this.paymentLinkService = paymentLinkService;
    }

    @PostMapping("/{transactionId}/complete")
    public PaymentLinkResponse completeDms(@PathVariable UUID transactionId,
                                           @Valid @RequestBody CompleteDmsRequest request,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        return paymentLinkService.completeDms(transactionId, request, principal);
    }

    @PostMapping("/{transactionId}/refund")
    public RefundResponse refund(@PathVariable UUID transactionId,
                                 @Valid @RequestBody RefundRequest request,
                                 @AuthenticationPrincipal UserPrincipal principal) {
        return paymentLinkService.refund(transactionId, request, principal);
    }

    @GetMapping("/{providerOrderId}/status")
    public TransactionResponse checkStatus(@PathVariable String providerOrderId) {
        return paymentLinkService.checkAndStatusUpdate(providerOrderId);
    }

    @GetMapping
    public az.millikart.pbl.dto.PagedResponse<TransactionResponse> list(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return paymentLinkService.listTransactions(pageable, principal);
    }
}
