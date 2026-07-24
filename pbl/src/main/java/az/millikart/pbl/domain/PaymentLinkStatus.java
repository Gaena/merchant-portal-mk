package az.millikart.pbl.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Lifecycle status of a payment link.
 */
public enum PaymentLinkStatus {
    /** Link is active and can be paid. */
    ACTIVE,
    /** Link has passed its expiration time. */
    EXPIRED,
    /** Link reached its allowed number of successful payments. */
    COMPLETED,
    /** Link manually canceled by the merchant. */
    CANCELED;

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

