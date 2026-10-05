package az.millikart.ecom.controller;

import az.millikart.common.security.UserPrincipal;
import az.millikart.ecom.dto.CursorPage;
import az.millikart.ecom.dto.EcomMoneyOperationResponse;
import az.millikart.ecom.dto.EcomMoneyRequest;
import az.millikart.ecom.dto.EcomResolveRequest;
import az.millikart.ecom.dto.EcomStatsResponse;
import az.millikart.ecom.dto.EcomTerminalResponse;
import az.millikart.ecom.dto.EcomTransactionResponse;
import az.millikart.ecom.service.EcomMoneyOperationService;
import az.millikart.ecom.service.EcomTransactionService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Выписка провайдера (Р-65), контракт — project_docs/modules/ecom.md §2.
@RestController
@RequestMapping("/api/v1/ecom/transactions")
public class EcomTransactionController {

    private final EcomTransactionService service;
    private final EcomMoneyOperationService moneyOperations;

    public EcomTransactionController(EcomTransactionService service, EcomMoneyOperationService moneyOperations) {
        this.service = service;
        this.moneyOperations = moneyOperations;
    }

    @GetMapping
    public CursorPage<EcomTransactionResponse> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateTo,
            @RequestParam(required = false) List<String> merchantRids,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentType,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return service.list(dateFrom, dateTo, merchantRids, minAmount, maxAmount, query, status, paymentType,
                cursor, size, principal);
    }

    @GetMapping("/stats")
    public EcomStatsResponse stats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateTo,
            @RequestParam(required = false) List<String> merchantRids,
            @RequestParam(required = false) String paymentType,
            @AuthenticationPrincipal UserPrincipal principal) {
        return service.stats(dateFrom, dateTo, merchantRids, paymentType, principal);
    }

    @GetMapping("/terminals")
    public List<EcomTerminalResponse> terminals(@AuthenticationPrincipal UserPrincipal principal) {
        return service.terminals(principal);
    }

    @GetMapping("/{orderId}")
    public EcomTransactionResponse order(@PathVariable String orderId,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        return service.order(orderId, principal);
    }

    // Возврат и списание заказа выписки (Р-125): права и причины — как у кнопок карточки.
    @PostMapping("/{orderId}/refund")
    public EcomMoneyOperationResponse refund(@PathVariable String orderId,
                                             @Valid @RequestBody EcomMoneyRequest request,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        return moneyOperations.refund(orderId, request.amount(), principal);
    }

    @PostMapping("/{orderId}/complete")
    public EcomMoneyOperationResponse capture(@PathVariable String orderId,
                                              @Valid @RequestBody EcomMoneyRequest request,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        return moneyOperations.capture(orderId, request.amount(), principal);
    }

    // Только SYSTEM_ADMIN, после сверки с провайдером (Р-125).
    @PostMapping("/{orderId}/resolve-outcome")
    public EcomTransactionResponse resolveOutcome(@PathVariable String orderId,
                                                  @Valid @RequestBody EcomResolveRequest request,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return moneyOperations.resolve(orderId, request.executed(), principal);
    }
}
