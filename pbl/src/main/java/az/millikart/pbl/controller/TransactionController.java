package az.millikart.pbl.controller;

import az.millikart.common.dto.PagedResponse;
import az.millikart.common.security.UserPrincipal;
import az.millikart.pbl.dto.*;
import az.millikart.pbl.service.PaymentLinkService;
import jakarta.validation.Valid;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    // Общий потолок страницы всех списков проекта.
    private static final int MAX_PAGE_SIZE = 200;

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

    // Только SYSTEM_ADMIN, после сверки с провайдером: executed — прошла ли неподтверждённая операция (Р-123).
    @PostMapping("/{transactionId}/resolve-outcome")
    public TransactionResponse resolveOutcome(@PathVariable UUID transactionId,
                                              @Valid @RequestBody ResolveOutcomeRequest request,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        return paymentLinkService.resolveOutcome(transactionId, request.executed(), principal);
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable UUID id,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return paymentLinkService.getTransaction(id, principal);
    }

    @GetMapping("/{identifier}/status")
    public TransactionResponse checkStatus(@PathVariable String identifier,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        return paymentLinkService.checkAndStatusUpdate(identifier, principal);
    }

    // id — уникальный хвост сортировки: без него строки переезжают между страницами (P2-1).
    @GetMapping
    public PagedResponse<TransactionResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        // Параметры приводятся, а не отвергаются: PageRequest.of бросает на page < 0 и size < 1 (P2-1).
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return paymentLinkService.listTransactions(pageable, principal);
    }
}
