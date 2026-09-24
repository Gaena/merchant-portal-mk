package az.millikart.directory.dto;

import jakarta.validation.constraints.NotBlank;

// Логин и пароль к провайдеру обязательны (Р-93): без них компания не создаст ни одного платежа.
// Логин — целиком, с префиксом владельца; сами ничего не подставляем.
public record CreateCompanyRequest(
        @NotBlank(message = "Company ID is required")
        String id,
        @NotBlank(message = "Company name is required")
        String name,
        @NotBlank(message = "Provider login is required")
        String providerLogin,
        @NotBlank(message = "Provider password is required")
        String providerPassword
) {
    // Пароль не попадает ни в логи, ни в сообщения об ошибках, где record печатается целиком.
    @Override
    public String toString() {
        return "CreateCompanyRequest[id=" + id + ", name=" + name + ", providerLogin=" + providerLogin
                + ", providerPassword=********]";
    }
}
