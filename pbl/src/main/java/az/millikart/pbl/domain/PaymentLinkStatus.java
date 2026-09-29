package az.millikart.pbl.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum PaymentLinkStatus {
    ACTIVE,
    EXPIRED,
    COMPLETED,
    CANCELED,
    // Терминал заблокирован (Р-39). Ставит и снимает только блокировка терминала в directory — ручная
    // или синхронизацией (Р-66); руками мерчанта не выйти. Разблокировка — в ACTIVE или, если срок
    // прошёл, в EXPIRED (Р-40).
    SUSPENDED;

    @JsonCreator
    public static PaymentLinkStatus fromValue(String value) {
        if (value == null) return null;
        if ("CANCELLED".equalsIgnoreCase(value)) {
            return CANCELED;
        }
        for (PaymentLinkStatus s : values()) {
            if (s.name().equalsIgnoreCase(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown status: " + value);
    }
}

