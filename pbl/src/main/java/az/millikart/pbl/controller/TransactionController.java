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

    // P3-7. Порядок с /{identifier}/status не спорит: у того два сегмента после базы, у этого
    // один. UUID в сигнатуре — сам по себе фильтр: «summary» или любое другое слово сюда не
    // попадёт, а вернёт 400 разбора пути, не чужую транзакцию.
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

    // P3-7: порядок был не задан вовсе — база отдавала строки как ей удобно, и «последние
    // операции» на любом экране были просто какими-то операциями. Довесок по id обязателен по
    // той же причине, что и у трёх справочников (P2-1): у двух платежей одна миллисекунда
    // createdAt бывает, и без уникального довеска строка попадёт то на обе соседние страницы,
    // то ни на одну.
    @GetMapping
    public PagedResponse<TransactionResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return paymentLinkService.listTransactions(pageable, principal);
    }
}
