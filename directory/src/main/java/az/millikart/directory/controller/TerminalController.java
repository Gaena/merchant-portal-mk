package az.millikart.directory.controller;

import az.millikart.common.security.UserPrincipal;
import az.millikart.directory.dto.CreateTerminalRequest;
import az.millikart.directory.dto.TerminalResponse;
import az.millikart.directory.dto.UpdateTerminalRequest;
import az.millikart.directory.service.TerminalService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @GetMapping
    public List<TerminalResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return terminalService.listTerminals(principal);
    }

    @GetMapping("/{id}")
    public TerminalResponse get(@PathVariable Integer id,
                                @AuthenticationPrincipal UserPrincipal principal) {
        return terminalService.getTerminal(id, principal);
    }

    @PatchMapping("/{id}")
    public TerminalResponse update(@PathVariable Integer id,
                                  @Valid @RequestBody UpdateTerminalRequest request,
                                  @AuthenticationPrincipal UserPrincipal principal) {
        return terminalService.updateTerminal(id, request, principal);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id,
                       @AuthenticationPrincipal UserPrincipal principal) {
        terminalService.deleteTerminal(id, principal);
    }
}
