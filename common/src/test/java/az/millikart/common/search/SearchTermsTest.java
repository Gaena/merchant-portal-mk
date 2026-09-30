package az.millikart.common.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Locale;
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

    // Регистр — по Locale.ROOT: в турецкой локали «I» стала бы «ı», и поиск по латинице ничего бы не нашёл.
    @Test
    void lowerCase_doesNotDependOnTheDefaultLocale() {
        Locale saved = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("%info%", SearchTerms.toLikePattern("INFO"));
        } finally {
            Locale.setDefault(saved);
        }
    }

    @Test
    void noTerm_isNoPattern() {
        assertNull(SearchTerms.toLikePattern(null));
    }
}
