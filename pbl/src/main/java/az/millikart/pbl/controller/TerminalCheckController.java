package az.millikart.pbl.controller;

import az.millikart.common.security.UserPrincipal;
import az.millikart.pbl.dto.TerminalCheckResponse;
import az.millikart.pbl.service.TerminalCheckService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Кнопка «Тест» у заведённого терминала (Р-70, Р-93). Живёт в pbl, а не рядом с терминалами в directory:
// проверка — пробный заказ у провайдера, а ходить к провайдеру умеет только pbl; отсюда и свой префикс.
// Ответ — всегда 200 с одним из четырёх исходов: это результат проверки, а не сбой запроса.
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
