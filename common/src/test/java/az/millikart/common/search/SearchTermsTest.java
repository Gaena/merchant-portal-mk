package az.millikart.common.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

// Поиск по спискам (P3-1). Экранирование — единственное, что не даёт вводу «%» вернуть всю таблицу:
// каждый LIKE объявляет ESCAPE '!', и паттерн обязан быть собран под него.
class SearchTermsTest {

    @Test
    void blankInput_isNoFilter() {
        assertNull(SearchTerms.normalize(null));
        assertNull(SearchTerms.normalize(""));
        assertNull(SearchTerms.normalize("  \t "));
    }

    @Test
    void input_isTrimmedAndCutTo100Characters() {
        assertEquals("shop", SearchTerms.normalize("  shop  "));
        assertEquals("a".repeat(100), SearchTerms.normalize(" " + "a".repeat(150)));
    }

    @Test
    void likeWildcards_becomeLiterals() {
        assertEquals("%100!%!_off%", SearchTerms.toLikePattern("100%_off"));
    }

    // '!' экранируется первым: иначе '!', добавленный перед '%', удвоился бы, и '%' снова стал бы шаблоном.
    @Test
    void theEscapeCharacterItself_isEscapedFirst() {
        assertEquals("%a!!b%", SearchTerms.toLikePattern("a!b"));
        assertEquals("%!!!%%", SearchTerms.toLikePattern("!%"));
    }

    // SEARCH-CASE: паттерн понижался в Java, а колонка — в базе, и «İ» они понижают по-разному: «İlham» не
    // находил «İlham …». Регистр паттерна теперь не трогаем — его понижает та же lower() базы, что и колонку.
    @Test
    void theCaseIsLeftToTheDatabase() {
        assertEquals("%İlham%", SearchTerms.toLikePattern("İlham"));
        assertEquals("%INFO%", SearchTerms.toLikePattern("INFO"));
    }

    @Test
    void noTerm_isNoPattern() {
        assertNull(SearchTerms.toLikePattern(null));
    }
}
