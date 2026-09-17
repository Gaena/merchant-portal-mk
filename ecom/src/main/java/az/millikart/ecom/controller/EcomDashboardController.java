package az.millikart.ecom.controller;

import az.millikart.common.security.UserPrincipal;
import az.millikart.ecom.dto.EcomDashboardResponse;
import az.millikart.ecom.service.EcomTransactionService;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Сводка главной страницы (Р-91) — по выписке провайдера, по всем терминалам скоупа. Префикс /api/v1/ecom,
// поэтому прокси Vite и nginx уже ведут его в этот сервис. Период обязателен и ограничен, как у выписки.
@RestController
@RequestMapping("/api/v1/ecom/dashboard")
public class EcomDashboardController {

    private final EcomTransactionService service;

    public EcomDashboardController(EcomTransactionService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public EcomDashboardResponse summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateTo,
            @AuthenticationPrincipal UserPrincipal principal) {
        return service.dashboard(dateFrom, dateTo, principal);
    }
}
