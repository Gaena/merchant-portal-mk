package az.millikart.directory.dto;

import az.millikart.directory.domain.TerminalStatus;
import java.time.Instant;

public record TerminalResponse(
        Integer id,
        String name,
        String login,
        // Номер терминала у провайдера — им терминал подписан на экранах (Р-96); пусто — подпись логином.
        String terminalRid,
        // Связан со справочником провайдера: название — провайдера, правкой не меняется (Р-67).
        boolean providerLinked,
        String companyId,
        TerminalStatus status,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt
) {
}
