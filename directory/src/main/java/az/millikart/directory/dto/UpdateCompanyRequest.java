package az.millikart.directory.dto;

import jakarta.validation.constraints.Size;

// Пустые поля не меняются. Пароль к провайдеру только записывается: прочитать его нельзя никому (Р-93).
// Потолки — как в CreateCompanyRequest.
public record UpdateCompanyRequest(
        @Size(max = 255, message = "Company name must be at most 255 characters")
        String name,
        String status,
        String providerLogin,
        @Size(max = 100, message = "Provider password must be at most 100 characters")
        String providerPassword
) {
    @Override
    public String toString() {
        return "UpdateCompanyRequest[name=" + name + ", status=" + status + ", providerLogin=" + providerLogin
                + ", providerPassword=" + (providerPassword == null ? null : "********") + "]";
    }
}
