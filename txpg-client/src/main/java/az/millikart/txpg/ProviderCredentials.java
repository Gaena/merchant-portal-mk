package az.millikart.txpg;

// Расшифрованные креды компании к провайдеру (Р-93). Пароль не печатается нигде — toString его маскирует.
public record ProviderCredentials(String login, String password) {

    @Override
    public String toString() {
        return "ProviderCredentials[login=" + login + ", password=********]";
    }
}
