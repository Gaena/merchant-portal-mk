package az.millikart.directory.controller;

import az.millikart.common.dto.PagedResponse;
import az.millikart.common.search.SearchTerms;
import az.millikart.common.security.UserPrincipal;
import az.millikart.directory.dto.CreateTerminalRequest;
import az.millikart.directory.dto.TerminalOptionResponse;
import az.millikart.directory.dto.TerminalPasswordResponse;
import az.millikart.directory.dto.TerminalResponse;
import az.millikart.directory.dto.UpdateTerminalRequest;
import az.millikart.directory.service.TerminalService;
import jakarta.validation.Valid;
import java.util.List;
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

@RestController
@RequestMapping("/api/v1/terminals")
public class TerminalController {

    private final TerminalService terminalService;

    public TerminalController(TerminalService terminalService) {
        this.terminalService = terminalService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TerminalResponse create(@Valid @RequestBody CreateTerminalRequest request,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return terminalService.createTerminal(request, principal);
    }

    // Потолок, общий для всех постраничных списков проекта — см. AuditLogController.
    private static final int MAX_PAGE_SIZE = 200;

    // Значения зажимаются, а не передаются как есть: PageRequest.of бросает на page < 0 и size < 1,
    // и это уходит клиенту как 500 со стектрейсом в логе. search (P3-1) приводится так же: пусто —
    // «нет поиска», слишком длинное обрезается.
    @GetMapping
    public PagedResponse<TerminalResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal UserPrincipal principal) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE));
        return terminalService.listTerminals(pageable, principal, SearchTerms.normalize(search));
    }

    // Р-45: фид для селекторов — id, name, login, status, без страниц и без пароля терминала.
    // Заблокированные терминалы в ответе есть, фильтрует потребитель — см.
    // TerminalService.listTerminalOptions. Маппинг стоит до /{id}: Spring сначала матчит
    // литеральный путь, и options не попадёт в Integer-переменную пути.
    @GetMapping("/options")
    public List<TerminalOptionResponse> options(@AuthenticationPrincipal UserPrincipal principal) {
        return terminalService.listTerminalOptions(principal);
    }

    @GetMapping("/{id}")
    public TerminalResponse get(@PathVariable Integer id,
                                @AuthenticationPrincipal UserPrincipal principal) {
        return terminalService.getTerminal(id, principal);
    }

    // Единственный путь, по которому пароль терминала уходит наружу: отдельный запрос, только для
    // SYSTEM_ADMIN, каждое чтение в журнале аудита. В TerminalResponse пароль как был
    // замаскирован, так и остаётся — см. TerminalService.revealPassword.
    @GetMapping("/{id}/password")
    public TerminalPasswordResponse password(@PathVariable Integer id,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        return terminalService.revealPassword(id, principal);
    }

    // Через status в теле терминал выводится из эксплуатации и возвращается обратно (Р-37).
    // DELETE нет: на терминал, через который прошёл платёж, ссылаются платёжные ссылки.
    // Отдельных /block и /unblock тоже нет: это одно поле одного ресурса.
    @PatchMapping("/{id}")
    public TerminalResponse update(@PathVariable Integer id,
                                  @Valid @RequestBody UpdateTerminalRequest request,
                                  @AuthenticationPrincipal UserPrincipal principal) {
        return terminalService.updateTerminal(id, request, principal);
    }
}
