package az.millikart.directory.dto;

import az.millikart.directory.domain.TerminalStatus;

// Для подписей и выпадающих списков (Р-45, Р-59); доступ тот же, что у GET /api/v1/terminals.
public record TerminalOptionResponse(
        Integer id,
        String name,
        String login,
        String terminalRid,
        TerminalStatus status
) {
}
