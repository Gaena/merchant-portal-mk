package az.millikart.ecom.controller;

import az.millikart.common.exception.InvalidStateException;
import az.millikart.common.security.Role;
import az.millikart.common.security.UserPrincipal;
import az.millikart.ecom.domain.ProviderTerminal;
import az.millikart.ecom.dto.ProviderTerminalResponse;
import az.millikart.ecom.repository.ProviderTerminalRepository;
import az.millikart.ecom.service.ProviderTerminalSyncService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Справочник терминалов провайдера для формы заведения нашего, только SYSTEM_ADMIN. Отдаётся слепок
// из нашей базы, а не живой запрос к шлюзу: форма не должна ждать чужую базу и падать вместе с ней.
@RestController
@RequestMapping("/api/v1/ecom/provider-terminals")
public class ProviderTerminalController {

    private final ProviderTerminalRepository repository;
    private final ProviderTerminalSyncService syncService;

    public ProviderTerminalController(ProviderTerminalRepository repository,
                                      ProviderTerminalSyncService syncService) {
        this.repository = repository;
        this.syncService = syncService;
    }

    // По умолчанию только активные: заводить терминал поверх снятого у провайдера незачем.
    // includeInactive — для разбора, куда делся знакомый администратору терминал.
    @GetMapping
    public List<ProviderTerminalResponse> list(
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireSystemAdmin(principal);
        List<ProviderTerminal> terminals = includeInactive
                ? repository.findAll()
                : repository.findByActiveTrueOrderByTitleAsc();
        return terminals.stream()
                .map(t -> new ProviderTerminalResponse(
                        t.getRid(), t.getTitle(), t.getLogin(), t.isActive(), t.getLastSeenAt()))
                .toList();
    }

    @PostMapping("/sync")
    public ProviderTerminalSyncService.SyncOutcome sync(@AuthenticationPrincipal UserPrincipal principal) {
        requireSystemAdmin(principal);
        return syncService.sync();
    }

    // Список терминалов провайдера — это карта его мерчантов целиком, включая чужих. Видеть её
    // вправе только системный администратор; мерчанту она не нужна даже для своей компании.
    private void requireSystemAdmin(UserPrincipal principal) {
        if (UserPrincipal.getRole(principal) != Role.SYSTEM_ADMIN) {
            throw new InvalidStateException("Access denied");
        }
    }
}
