package az.millikart.pbl.domain;

public enum PaymentType {
    // Single Message System: авторизация и списание одним шагом.
    SMS,
    // Dual Message System: сначала авторизация, списание (complete) позже.
    DMS
}
