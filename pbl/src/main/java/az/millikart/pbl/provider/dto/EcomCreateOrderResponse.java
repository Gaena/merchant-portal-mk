package az.millikart.pbl.provider.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EcomCreateOrderResponse(
        @JsonProperty("order") Order order
) {
    public record Order(
            @JsonProperty("hppUrl") String hppUrl,
            @JsonProperty("id") long id,
            @JsonProperty("status") String status,
            @JsonProperty("password") String password
    ) {
        // P0-9: сгенерированный toString рекорда печатает все компоненты, включая пароль заказа,
        // и любой log.debug("... {}", response) вывел бы его дословно. Поэтому маска стоит здесь,
        // на типе, а не на каждом вызове; null остаётся видимым — заказ без пароля стоит заметить.
        @Override
        public String toString() {
            return "Order[hppUrl=" + hppUrl + ", id=" + id + ", status=" + status
                    + ", password=" + (password == null ? "null" : "***") + "]";
        }
    }
}
