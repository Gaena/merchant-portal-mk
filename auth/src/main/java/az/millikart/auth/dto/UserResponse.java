package az.millikart.auth.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String fullName,
        String role,
        String companyId,
        String status,
        Instant createdAt,
        // Пароль задал не владелец и ещё не сменил его при входе (Р-100).
        boolean passwordChangeRequired,
        // Р-132: может ли создавать DMS-ссылки; у ролей вне компании всегда true.
        boolean dmsLinksAllowed,
        // Р-131: терминалы сотрудника по возрастанию номера; у остальных ролей — пусто.
        List<Integer> terminalIds
) {
}
