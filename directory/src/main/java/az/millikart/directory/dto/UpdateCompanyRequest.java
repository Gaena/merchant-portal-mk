package az.millikart.directory.dto;

// Пустые поля не меняются. Пароль к провайдеру только записывается: прочитать его нельзя никому (Р-93).
public record UpdateCompanyRequest(
        String name,
        String status,
        String providerLogin,
        String providerPassword
) {
    @Override
    public String toString() {
        return "UpdateCompanyRequest[name=" + name + ", status=" + status + ", providerLogin=" + providerLogin
                + ", providerPassword=" + (providerPassword == null ? null : "********") + "]";
    }
}
