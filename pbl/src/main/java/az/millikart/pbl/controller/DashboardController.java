package az.millikart.pbl.controller;

import az.millikart.common.security.UserPrincipal;
import az.millikart.pbl.dto.DashboardSummaryResponse;
import az.millikart.pbl.service.DashboardService;
import java.time.Instant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Сводка главной страницы (P3-7). Отдельный префикс, а не /api/v1/transactions/summary: рядом
// живёт GET /api/v1/transactions/{id}, и «summary» уехало бы в разбор UUID.
// Префикс новый — правило прокси на :8080 заведено в vite.config.ts и в nginx.
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    // from и to — ISO-8601 (2026-08-24T00:00:00Z), оба необязательны: по умолчанию семь
    // календарных суток по сегодняшний день. Границы окна проверяет сервис и отвечает 400,
    // а не молча зажимает.
    @GetMapping("/summary")
    public DashboardSummaryResponse summary(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @AuthenticationPrincipal UserPrincipal principal) {
        return dashboardService.summary(from, to, principal);
    }
}
