package az.millikart.pbl.domain;

/**
 * Type of payment processing supported by the acquiring gateway.
 */
public enum PaymentType {
    /** Single Message System - authorization and capture in a single step. */
    SMS,
    /** Dual Message System - authorization first, capture (complete) later. */
    DMS
}
