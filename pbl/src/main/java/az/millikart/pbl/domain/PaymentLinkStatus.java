package az.millikart.pbl.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum PaymentLinkStatus {
    ACTIVE,
    EXPIRED,
    COMPLETED,
    CANCELED,
    // Терминал ссылки заблокирован, платить по ней нельзя (Р-39). Ставит только блокировка
    // терминала, снимает только разблокировка (TerminalService.applyStatusChange): руками мерчанта
    // отсюда не выйти — снятие вернуло бы оплачиваемую ссылку на снятый с обслуживания терминал.
    // Разблокировка возвращает такие ссылки в ACTIVE или в EXPIRED, если срок уже прошёл (Р-40).
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

