package az.millikart.pbl.provider.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record EcomCreateOrderRequest(
        @JsonProperty("order") Order order
) {
    public record Order(
            @JsonProperty("typeRid") String typeRid,
            @JsonProperty("ridByMerchant") String ridByMerchant,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currency") String currency,
            @JsonProperty("description") String description,
            @JsonProperty("language") String language,
            @JsonProperty("hppRedirectUrl") String hppRedirectUrl,
            @JsonProperty("subMerchant") SubMerchant subMerchant
    ) {}

    public record SubMerchant(
            @JsonProperty("url") String url
    ) {}
}
