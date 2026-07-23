package az.millikart.directory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTerminalRequest(
        @NotNull(message = "Terminal ID is required")
        Integer id,

        @NotBlank(message = "Terminal name is required")
        String name,

        @NotBlank(message = "Terminal login is required")
        String login,

        @NotBlank(message = "Terminal password is required")
        String password,

        @NotBlank(message = "Company ID is required")
        String companyId
) {
}
