package az.millikart.auth.dto;

// Секрет второго фактора для приложения-аутентификатора — base32 для ручного ввода и адрес otpauth:// для
// QR-кода (Р-141). Отдаётся один раз, при подключении.
public record MfaSetupResponse(
        String secret,
        String otpauthUri
) {

    @Override
    public String toString() {
        return "MfaSetupResponse[secret=***, otpauthUri=***]";
    }
}
