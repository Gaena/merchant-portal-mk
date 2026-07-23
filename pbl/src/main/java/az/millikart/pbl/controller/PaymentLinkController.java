package az.millikart.pbl.controller;

import az.millikart.common.security.UserPrincipal;
import az.millikart.pbl.domain.PaymentLinkStatus;
import az.millikart.pbl.dto.CreatePaymentLinkRequest;
import az.millikart.pbl.dto.PagedResponse;
import az.millikart.pbl.dto.PaymentLinkResponse;
import az.millikart.pbl.dto.PaymentLinkSummaryResponse;
import az.millikart.pbl.dto.UpdatePaymentLinkRequest;
import az.millikart.pbl.service.PaymentLinkService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for managing payment links.
 */
@RestController
@RequestMapping("/api/v1/payment-links")
public class PaymentLinkController {

    private final PaymentLinkService paymentLinkService;

    public PaymentLinkController(PaymentLinkService paymentLinkService) {
        this.paymentLinkService = paymentLinkService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentLinkResponse create(@Valid @RequestBody CreatePaymentLinkRequest request,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        return paymentLinkService.create(request, principal);
    }

    @PatchMapping("/{id}")
    public PaymentLinkResponse update(@PathVariable UUID id,
                                      @Valid @RequestBody UpdatePaymentLinkRequest request,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        return paymentLinkService.update(id, request, principal);
    }

    @GetMapping("/{id}")
    public PaymentLinkResponse get(@PathVariable UUID id,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return paymentLinkService.get(id, principal);
    }

    @GetMapping
    public PagedResponse<PaymentLinkSummaryResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer terminal,
            @RequestParam(required = false) PaymentLinkStatus status,
            @AuthenticationPrincipal UserPrincipal principal) {
        Pageable pageable = PageRequest.of(page, size);
        return paymentLinkService.list(terminal, status, pageable, principal);
    }
}
