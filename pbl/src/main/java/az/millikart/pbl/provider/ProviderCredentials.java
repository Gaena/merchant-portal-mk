package az.millikart.pbl.provider;

// Креды компании к провайдеру, уже расшифрованные (Р-93). Логин — целиком, как ввёл администратор;
// пароль не печатается нигде — toString маскирует его.
public record ProviderCredentials(String login, String password) {

    @Override
    public String toString() {
        return "ProviderCredentials[login=" + login + ", password=********]";
    }
}
