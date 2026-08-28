package az.millikart.pbl.provider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.web.util.UriComponentsBuilder;

// Что из данных эквайера можно писать в лог и в колонку provider_response, а что нельзя (P0-9).
// Секрет здесь один — пароль заказа: эквайер выдаёт его при создании (§5.3), возвращает на каждом
// опросе статуса при orderDetailLevel=2 (§5.8.3) и сервис шлёт его обратно в query всех
// последующих вызовов (Р-25). urlForLog и withoutSecrets — единственный санкционированный путь.
public final class ProviderPayloads {

    // Ключи, которые снимаются с payload перед логом и записью. Расширять здесь, а не на вызовах.
    static final Set<String> SECRET_KEYS = Set.of("password");

    // Что urlForLog возвращает для неразобранного адреса. Намеренно не сам адрес: строка, которую
    // мы не смогли прочесть, — это строка, про которую мы не знаем, есть ли в ней пароль.
    static final String UNPARSEABLE_URL = "<unparseable url>";

    private ProviderPayloads() {
    }

    // Адрес без query-строки: всё после «?» в лог не идёт (P0-9). Режется query целиком, а не
    // маскируется один параметр, намеренно: кроме пароля там только константы вроде
    // orderDetailLevel=2, а replaceQueryParam("password", "***") ДОБАВИЛ бы параметр в адрес,
    // где его не было, и лог показывал бы password=*** на вызовах без пароля. url может быть null.
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
        // Непрозрачный URI ("mailto:a@b?x=1") держит «query» внутри scheme-specific part, которую
        // replaceQuery не трогает; «?» бывает и во фрагменте. Гарантия здесь — «в логе нет
        // query-строки», а не «нет query у тех адресов, которые мы строим сегодня».
        int query = bare.indexOf('?');
        return query < 0 ? bare : bare.substring(0, query);
    }

    // Копия payload без секретов. Снимается только верхнеуровневый password — туда его кладёт
    // контракт (§5.8.3). Вложенное остаётся намеренно: в srcToken лежит маскированный displayName,
    // а не PAN, в trans[] и terminal — идентификаторы. Вход не меняется, так что immutable map
    // допустима; на null возвращается null, чтобы отсутствие payload было видно в логе. P0-9.
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

    // Одно правило на весь пакет: значением payload считается только скаляр — String или Number.
    // Не-скаляр (объект, список, boolean) означает, что форма ответа не та, что в контракте.
    // Для tran.match.ridByPmo это граница между подтверждённой операцией и неизвестным исходом
    // (P1-8b, Р-23): String.valueOf на map дал бы непустое "{a=1}" и сошёл бы за подтверждение.
    public static String scalarText(Object value) {
        if (!(value instanceof String) && !(value instanceof Number)) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }
}
