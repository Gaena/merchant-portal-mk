package az.millikart.txpg.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record EcomCreateOrderRequest(
        @JsonProperty("order") Order order
) {
    // tdsPresetAreq — клиент для 3DS (Р-96): только у одноразовой ссылки; пустой блок не отправляется.
    public record Order(
            @JsonProperty("typeRid") String typeRid,
            @JsonProperty("ridByMerchant") String ridByMerchant,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currency") String currency,
            @JsonProperty("description") String description,
            @JsonProperty("language") String language,
            @JsonProperty("hppRedirectUrl") String hppRedirectUrl,
            @JsonProperty("subMerchant") SubMerchant subMerchant,
            @JsonInclude(JsonInclude.Include.NON_NULL) @JsonProperty("tdsPresetAreq") TdsPresetAreq tdsPresetAreq
    ) {}

    public record SubMerchant(
            @JsonProperty("url") String url
    ) {}

    // Данные плательщика — персональные: в логи они не попадают, toString их прячет.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TdsPresetAreq(
            @JsonProperty("cardholderName") String cardholderName,
            @JsonProperty("email") String email,
            @JsonProperty("mobilePhone") Phone mobilePhone
    ) {
        @Override
        public String toString() {
            return "TdsPresetAreq[cardholderName=" + (cardholderName != null ? "***" : null)
                    + ", email=" + (email != null ? "***" : null)
                    + ", mobilePhone=" + (mobilePhone != null ? "***" : null) + "]";
        }
    }

    public record Phone(
            @JsonProperty("subscriber") String subscriber,
            @JsonProperty("cc") String cc
    ) {}
}
