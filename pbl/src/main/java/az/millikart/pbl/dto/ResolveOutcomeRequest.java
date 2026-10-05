package az.millikart.pbl.dto;

import jakarta.validation.constraints.NotNull;

// executed — итог сверки с провайдером: true — операция прошла и записывается у нас как подтверждённая,
// false — не прошла, и запрет просто снимается (Р-123).
public record ResolveOutcomeRequest(@NotNull(message = "executed is required") Boolean executed) {
}
