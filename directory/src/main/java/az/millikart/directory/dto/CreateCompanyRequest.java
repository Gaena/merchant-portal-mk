package az.millikart.directory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Логин и пароль к провайдеру обязательны (Р-93): без них компания не создаст ни одного платежа.
// Логин — целиком, с префиксом владельца; сами ничего не подставляем. Пароль в базе — шифротекст в
// varchar(512): 100 знаков по 3 байта UTF-8 дают 440 знаков base64 (DB-CONSTRAINT-500).
public record CreateCompanyRequest(
        @NotBlank(message = "Company ID is required")
        @Size(max = 255, message = "Company ID must be at most 255 characters")
        String id,
        @NotBlank(message = "Company name is required")
        @Size(max = 255, message = "Company name must be at most 255 characters")
        String name,
        @NotBlank(message = "Provider login is required")
        String providerLogin,
        @NotBlank(message = "Provider password is required")
        @Size(max = 100, message = "Provider password must be at most 100 characters")
        String providerPassword,
        // VÖEN для чека плательщика (Р-129): необязателен, но если задан — ровно 10 цифр.
        @Pattern(regexp = "\\d{10}", message = "Tax ID (VÖEN) must be exactly 10 digits")
        String taxId
) {
    // Пароль не попадает ни в логи, ни в сообщения об ошибках, где record печатается целиком.
    @Override
    public String toString() {
        return "CreateCompanyRequest[id=" + id + ", name=" + name + ", providerLogin=" + providerLogin
                + ", providerPassword=********, taxId=" + taxId + "]";
    }
}
