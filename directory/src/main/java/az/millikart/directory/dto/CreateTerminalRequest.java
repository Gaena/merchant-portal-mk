package az.millikart.directory.dto;

import jakarta.validation.constraints.NotBlank;

// Заведение терминала — только SYSTEM_ADMIN и только выбором из справочника провайдера (Р-93).
// Номер выдаёт база (Р-81), название и логин приходят из справочника (Р-67), пароля у терминала нет:
// к провайдеру ходят от имени компании.
public record CreateTerminalRequest(
        @NotBlank(message = "Company ID is required")
        String companyId,
        @NotBlank(message = "Provider terminal (merchantRid) is required")
        String merchantRid
) {
}
