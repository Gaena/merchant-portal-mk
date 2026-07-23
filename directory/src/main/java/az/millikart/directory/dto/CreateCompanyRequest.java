package az.millikart.directory.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCompanyRequest(
        @NotBlank(message = "Company ID is required")
        String id,

        @NotBlank(message = "Company name is required")
        String name
) {
}
