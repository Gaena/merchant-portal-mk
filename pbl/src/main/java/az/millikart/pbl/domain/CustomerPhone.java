package az.millikart.pbl.domain;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Только азербайджанский номер (Р-96); хранится как +994XXXXXXXXX, провайдеру уходит разобранным:
// {"cc": "994", "subscriber": "XXXXXXXXX"}.
public final class CustomerPhone {

    public static final String COUNTRY_CODE = "994";

    private static final Pattern SEPARATORS = Pattern.compile("[\\s\\-()]");
    private static final Pattern AZERBAIJANI = Pattern.compile("(?:\\+994|994|0)(\\d{9})");

    private CustomerPhone() {
    }

    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher matcher = AZERBAIJANI.matcher(SEPARATORS.matcher(raw.trim()).replaceAll(""));
        return matcher.matches() ? Optional.of("+" + COUNTRY_CODE + matcher.group(1)) : Optional.empty();
    }

    // У ссылок до Р-96 телефон — произвольная строка: не разобрался — пусто, и платёж идёт без телефона.
    public static Optional<String> subscriberOf(String stored) {
        return normalize(stored).map(phone -> phone.substring(1 + COUNTRY_CODE.length()));
    }
}
