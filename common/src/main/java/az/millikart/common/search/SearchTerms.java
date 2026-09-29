package az.millikart.common.search;

import java.util.Locale;

// Единственное место нормализации поиска и LIKE-паттерна (P3-1): правила экранирования не должны
// разъехаться. LIKE '%…%' без индекса — решение: на здешних объёмах это доли миллисекунды; pg_trgm и
// полнотекстовый поиск не добавлять, пока нет измеренной проблемы.
public final class SearchTerms {

    // Его обязан объявить каждый LIKE по toLikePattern. Не бэкслеш: паттерн уходит в JPQL, Criteria и
    // нативный SQL на H2 и PostgreSQL, и бэкслешу на части слоёв нужно своё экранирование.
    public static final char LIKE_ESCAPE = '!';

    // Длиннее — режем, а не отвергаем: строка поиска не стоит 400, а колонки всё равно короче.
    private static final int MAX_LENGTH = 100;

    private SearchTerms() {
    }

    // Пустая или пробельная строка равна отсутствию параметра.
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > MAX_LENGTH ? trimmed.substring(0, MAX_LENGTH) : trimmed;
    }

    // Для lower(колонка) LIKE :pattern ESCAPE '!'; без экранирования % вернёт всю таблицу. Сначала
    // экранируется сам '!', иначе его удвоят следующие замены.
    public static String toLikePattern(String term) {
        if (term == null) {
            return null;
        }
        String escaped = term
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped.toLowerCase(Locale.ROOT) + "%";
    }
}
