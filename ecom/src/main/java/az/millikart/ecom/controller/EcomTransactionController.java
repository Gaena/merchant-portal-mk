package az.millikart.ecom.controller;

import az.millikart.common.security.UserPrincipal;
import az.millikart.ecom.dto.CursorPage;
import az.millikart.ecom.dto.EcomOperationResponse;
import az.millikart.ecom.dto.EcomStatsResponse;
import az.millikart.ecom.dto.EcomTerminalResponse;
import az.millikart.ecom.dto.EcomTransactionResponse;
import az.millikart.ecom.service.EcomTransactionService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Вкладка E-commerce: платежи, которые мы не порождали.
 *
 * Адреса намеренно свои (`/api/v1/ecom/...`), а не общие с платёжными ссылками: это другой
 * источник, другой набор полей и другой SLA. Портал решает, какую вкладку показывать мерчанту,
 * по наличию привязок — один общий грид поверх двух источников потребовал бы либо репликации,
 * либо склейки страниц в браузере, и то и другое обсуждается отдельно.
 *
 * Период обязателен во всех трёх чтениях: без него это полный скан операционной базы шлюза.
 */
@RestController
@RequestMapping("/api/v1/ecom/transactions")
public class EcomTransactionController {

    private final EcomTransactionService service;

    public EcomTransactionController(EcomTransactionService service) {
        this.service = service;
    }

    @GetMapping
    public CursorPage<EcomTransactionResponse> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateTo,
            @RequestParam(required = false) List<String> terminalIds,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return service.list(dateFrom, dateTo, terminalIds, minAmount, maxAmount, query, cursor, size, principal);
    }

    @GetMapping("/stats")
    public EcomStatsResponse stats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateTo,
            @AuthenticationPrincipal UserPrincipal principal) {
        return service.stats(dateFrom, dateTo, principal);
    }

    // Терминалы, встречающиеся в платежах периода, — источник для фильтра. Отдельным запросом,
    // а не distinct по странице: в фильтре должны быть все терминалы периода, а не те, что
    // попали в первые двадцать пять строк.
    @GetMapping("/terminals")
    public List<EcomTerminalResponse> terminals(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateTo,
            @AuthenticationPrincipal UserPrincipal principal) {
        return service.terminals(dateFrom, dateTo, principal);
    }

    // История заказа. У платёжных ссылок её приходится собирать из собственных меток, здесь она
    // есть у провайдера готовой — это и есть список операций по заказу.
    @GetMapping("/{orderId}/operations")
    public List<EcomOperationResponse> operations(@PathVariable String orderId,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return service.operations(orderId, principal);
    }
}
