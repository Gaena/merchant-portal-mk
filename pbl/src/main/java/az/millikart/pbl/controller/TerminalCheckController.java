package az.millikart.pbl.controller;

import az.millikart.common.security.UserPrincipal;
import az.millikart.pbl.dto.TerminalCheckRequest;
import az.millikart.pbl.dto.TerminalCheckResponse;
import az.millikart.pbl.service.TerminalCheckService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Кнопка «Тест» у терминала.
 *
 * Живёт в `pbl`, а не рядом с остальными эндпоинтами терминалов в `directory`: проверка — это
 * заведение пробного заказа у провайдера, а ходить к провайдеру умеет только `pbl`. Отсюда и
 * отдельный префикс — `/api/v1/terminals/**` целиком маршрутизируется в `directory`.
 *
 * Ответ всегда 200 с одним из четырёх исходов, даже когда пароль неверный или провайдер
 * недоступен: это результат проверки, а не сбой запроса.
 */
@RestController
@RequestMapping("/api/v1/acquiring/terminal-checks")
public class TerminalCheckController {

    private final TerminalCheckService service;

    public TerminalCheckController(TerminalCheckService service) {
        this.service = service;
    }

    /** Учётные данные терминала, который ещё только заводят. */
    @PostMapping
    public TerminalCheckResponse checkNew(@Valid @RequestBody TerminalCheckRequest request,
                                          @AuthenticationPrincipal UserPrincipal principal) {
        return service.checkNew(request.login(), request.password(), principal);
    }

    /** Учётные данные уже заведённого терминала — из базы, наружу пароль не уходит. */
    @PostMapping("/{terminalId}")
    public TerminalCheckResponse checkExisting(@PathVariable Integer terminalId,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        return service.checkExisting(terminalId, principal);
    }
}
