package az.millikart.common.search;

import java.util.Locale;

// Единственное место, где нормализуется строка поиска списков и строится LIKE-паттерн (P3-1), —
// чтобы правила экранирования не разъехались между сервисами.
// Намеренно LIKE '%…%' и ничего умнее: индекс он не использует и читает таблицу, но на здешних
// объёмах это доли миллисекунды. Полнотекстовый поиск, pg_trgm и GIN рассмотрены и отклонены —
// не добавлять, пока не появится измеренная проблема. Отсутствие индекса здесь — решение.
public final class SearchTerms {

    // Экранирующий символ, который обязан объявить каждый LIKE по паттерну из toLikePattern.
    // Не бэкслеш: тот же паттерн уходит в JPQL, в Criteria и в нативный SQL, на H2 и на
    // PostgreSQL, и бэкслешу на части этих слоёв нужно собственное экранирование — классический
    // источник «в тестах работает, в проде нет».
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

    // Готовый паттерн для lower(колонка) LIKE :pattern ESCAPE '!'. Экранирование обязательно:
    // без него введённый пользователем % возвращает всю таблицу, а _ совпадает с любым символом.
    // Порядок замен именно такой — сначала сам экранирующий символ, иначе следующие две замены
    // добавят '!' и он же будет экранирован повторно.
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
