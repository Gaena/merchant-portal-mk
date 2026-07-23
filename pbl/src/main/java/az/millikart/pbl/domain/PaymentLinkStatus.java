package az.millikart.pbl.domain;

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
    CANCELED
}
