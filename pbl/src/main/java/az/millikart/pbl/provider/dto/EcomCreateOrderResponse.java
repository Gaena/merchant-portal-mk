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
    ) {}
}
