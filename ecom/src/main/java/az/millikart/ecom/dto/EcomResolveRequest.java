package az.millikart.ecom.dto;

import jakarta.validation.constraints.NotNull;

// Итог сверки с провайдером (Р-125): у заказа выписки он идёт только в журнал — деньги видны в выписке.
public record EcomResolveRequest(@NotNull(message = "executed is required") Boolean executed) {
}
