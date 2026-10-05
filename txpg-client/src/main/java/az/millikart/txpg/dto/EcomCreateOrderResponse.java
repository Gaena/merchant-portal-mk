package az.millikart.txpg.dto;

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
        // P0-9: маска пароля — на типе, а не на вызовах: сгенерированный toString вывел бы его в любой
        // лог. null остаётся видимым — заказ без пароля стоит заметить.
        @Override
        public String toString() {
            return "Order[hppUrl=" + hppUrl + ", id=" + id + ", status=" + status
                    + ", password=" + (password == null ? "null" : "***") + "]";
        }
    }
}
