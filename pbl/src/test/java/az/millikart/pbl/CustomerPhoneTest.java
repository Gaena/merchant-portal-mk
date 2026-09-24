package az.millikart.pbl;

import az.millikart.pbl.domain.CustomerPhone;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Телефон клиента — только азербайджанский (Р-96): провайдер ждёт его как {"cc": "994", "subscriber": "…"}.
// Форма и API принимают привычные записи номера; всё прочее — отказ, а не догадка о коде страны.
class CustomerPhoneTest {

    @ParameterizedTest
    @ValueSource(strings = {"+994703301025", "994703301025", "0703301025", "+994 (70) 330-10-25", " 070 330 10 25 "})
    void azerbaijaniNumbers_areStoredWithTheCountryCode(String raw) {
        Assertions.assertEquals(Optional.of("+994703301025"), CustomerPhone.normalize(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"703301025", "+7 999 123 45 67", "+99470330102", "+9947033010255", "call me", ""})
    void anythingElse_isNotANumber(String raw) {
        Assertions.assertEquals(Optional.empty(), CustomerPhone.normalize(raw));
    }

    @Test
    void theProviderGetsTheNumberWithoutTheCountryCode() {
        Assertions.assertEquals(Optional.of("703301025"), CustomerPhone.subscriberOf("+994703301025"));
        Assertions.assertEquals(Optional.empty(), CustomerPhone.subscriberOf(null));
    }
}
