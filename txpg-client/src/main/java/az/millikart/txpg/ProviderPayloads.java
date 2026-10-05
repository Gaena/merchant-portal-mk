package az.millikart.txpg;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.web.util.UriComponentsBuilder;

// Что из данных эквайера можно в лог и в provider_response (P0-9). Секрет один — пароль заказа: он
// приходит при создании (§5.3) и в статусе (§5.8.3) и уходит в query вызовов (Р-25). Адреса и payload
// эквайера пишутся только через urlForLog и withoutSecrets.
public final class ProviderPayloads {

    // Новый секретный ключ — сюда, а не на вызовы.
    static final Set<String> SECRET_KEYS = Set.of("password");

    // Не сам адрес: в неразобранной строке может быть пароль.
    static final String UNPARSEABLE_URL = "<unparseable url>";

    private ProviderPayloads() {
    }

    // Query режется целиком, а не маскируется password (P0-9): кроме пароля там только константы, а
    // replaceQueryParam("password", "***") добавил бы параметр в адрес, где его не было.
    public static String urlForLog(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        String bare;
        try {
            bare = UriComponentsBuilder.fromUriString(url)
                    .replaceQuery(null)
                    .toUriString();
        } catch (RuntimeException e) {
            return UNPARSEABLE_URL;
        }
        // replaceQuery не трогает «?» в непрозрачном URI ("mailto:a@b?x=1") и во фрагменте, а гарантия —
        // «в логе нет query» для любого адреса.
        int query = bare.indexOf('?');
        return query < 0 ? bare : bare.substring(0, query);
    }

    // Снимается только верхнеуровневый password — там его держит контракт (§5.8.3); во вложенном
    // секретов нет, srcToken — маска, а не PAN. null остаётся null — отсутствие payload видно в логе.
    public static Map<String, Object> withoutSecrets(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>(payload);
        for (String key : SECRET_KEYS) {
            copy.remove(key);
        }
        return copy;
    }

    // Единственное правило пакета: значение — только String или Number, иначе форма не по контракту.
    // Для ridByPmo это граница подтверждения (P1-8b, Р-23): String.valueOf на map дал бы "{a=1}" и
    // сошёл бы за подтверждение.
    public static String scalarText(Object value) {
        if (!(value instanceof String) && !(value instanceof Number)) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }
}
