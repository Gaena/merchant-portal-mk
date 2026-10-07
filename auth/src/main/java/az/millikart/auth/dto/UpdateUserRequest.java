package az.millikart.auth.dto;

import az.millikart.common.validation.ValidPassword;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateUserRequest(
        @Size(max = 255, message = "Full name must be at most 255 characters")
        String fullName,
        String role,
        @ValidPassword
        String password,
        String status,
        // null — не менять, пустая строка — снять (только ролям вне компании); менять может только
        // SYSTEM_ADMIN (Р-90).
        String companyId,
        // Р-131: null — не менять, список — заменить целиком. Сотруднику — хотя бы один терминал его компании.
        @Size(max = 500, message = "At most 500 terminals can be assigned")
        List<@NotNull(message = "Terminal ID must not be null") Integer> terminalIds,
        // Р-132: null — не менять. Запрет — только ролям компании; руководитель себе не меняет.
        Boolean dmsLinksAllowed
) {
}
