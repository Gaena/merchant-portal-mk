package az.millikart.pbl.domain;

/**
 * Defines how many times a payment link may be used.
 */
public enum UsageType {
    /** The link can be paid only once. */
    SINGLE,
    /** The link can be paid multiple times, up to {@code maxPayments}. */
    MULTIPLE
}
