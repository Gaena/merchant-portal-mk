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

// Статистика оплат по ссылкам (P3-7, Р-91). Свой префикс, а не /transactions/summary: «summary» ушло
// бы в разбор UUID у GET /transactions/{id}. Префикс заведён в прокси vite.config.ts и nginx.
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    // ISO-8601; без них — семь суток по сегодня. Кривое окно — 400 от сервиса, а не молчаливый зажим.
    @GetMapping("/summary")
    public DashboardSummaryResponse summary(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @AuthenticationPrincipal UserPrincipal principal) {
        return dashboardService.summary(from, to, principal);
    }
}
