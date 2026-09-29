package az.millikart.pbl.controller;

import az.millikart.common.security.UserPrincipal;
import az.millikart.pbl.dto.TerminalCheckResponse;
import az.millikart.pbl.service.TerminalCheckService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Кнопка «Тест» (Р-70, Р-93) — в pbl, потому что к провайдеру ходит только pbl. Исход проверки —
// 200 с одним из четырёх исходов, а не код ошибки: это результат, а не сбой запроса.
@RestController
@RequestMapping("/api/v1/acquiring/terminal-checks")
public class TerminalCheckController {

    private final TerminalCheckService service;

    public TerminalCheckController(TerminalCheckService service) {
        this.service = service;
    }

    @PostMapping("/{terminalId}")
    public TerminalCheckResponse checkExisting(@PathVariable Integer terminalId,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        return service.checkExisting(terminalId, principal);
    }
}
