package az.millikart.directory.dto;

import jakarta.validation.constraints.NotBlank;

// Номер выдаёт база (Р-81), название и логин — справочник провайдера (Р-67), пароля у терминала нет (Р-93).
public record CreateTerminalRequest(
        @NotBlank(message = "Company ID is required")
        String companyId,
        @NotBlank(message = "Provider terminal (merchantRid) is required")
        String merchantRid
) {
}
