package az.millikart.ecom.repository;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assertions;

// Белый список схемы шлюза: только таблицы и колонки из SQL провайдера от 14.09.2026, исполненного на схеме,
// собранной по нему (Р-74, Р-79, Р-94). Отсутствующая колонка роняет весь запрос (ORA-00904), а опечатку
// чёрный список не ловит — она ни на что запрещённое не похожа. Новая колонка — сюда, со ссылкой на источник.
final class TxpgColumns {

    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "order_", Set.of("id", "ridbymerchant", "status", "prevstatus", "description", "amt", "ccy",
                    "createtime", "merchantid"),
            "tran", Set.of("id", "orderid", "merchantid", "ridbyacq", "rrn", "origtime", "pmoresultcode", "tranamt",
                    "clearamt", "tranccy", "trantype", "phase", "voidkind", "authkind"),
            "merchant", Set.of("id", "rid", "title"),
            "token", Set.of("orderid", "displayname"),
            "login", Set.of("id", "login", "status", "ownerkind", "terminalid"),
            "terminal", Set.of("id", "rid", "status", "merchantid"),
            "terminalpmo", Set.of("terminalid"),
            "login2merchant", Set.of("loginid", "merchantid", "status"));

    // getHighIdForTime в пакете провайдера нет.
    private static final Set<String> ALLOWED_FUNCTIONS = Set.of("RDX_Action.getLowIdForTime");

    // tk — подзапрос по token в карточке заказа, своей таблицы у него нет.
    private static final Map<String, String> DERIVED_ALIASES = Map.of("tk", "token");

    private static final Set<String> KEYWORDS = Set.of("where", "on", "join", "left", "group", "order");
    private static final Pattern TABLE = Pattern.compile("TXPG\\.(\\w+)(?![.\\w])(?:\\s+(\\w+))?");
    private static final Pattern FUNCTION = Pattern.compile("TXPG\\.(\\w+\\.\\w+)\\(");
    private static final Pattern REFERENCE = Pattern.compile("\\b([a-z]\\w*)\\.(\\w+)\\b");

    private TxpgColumns() {
    }

    static void assertOnlyProviderColumns(String sql) {
        Map<String, String> tableByAlias = new HashMap<>(DERIVED_ALIASES);
        Matcher table = TABLE.matcher(sql);
        while (table.find()) {
            String name = table.group(1);
            Assertions.assertTrue(ALLOWED.containsKey(name), "table " + name + " is not in the provider SQL: " + sql);
            String alias = table.group(2);
            if (alias != null && !KEYWORDS.contains(alias.toLowerCase(Locale.ROOT))) {
                tableByAlias.put(alias, name);
            }
        }
        Matcher function = FUNCTION.matcher(sql);
        while (function.find()) {
            Assertions.assertTrue(ALLOWED_FUNCTIONS.contains(function.group(1)),
                    function.group(1) + " is not a provider function we rely on: " + sql);
        }
        Matcher reference = REFERENCE.matcher(sql);
        while (reference.find()) {
            String alias = reference.group(1);
            String column = reference.group(2);
            String tableName = tableByAlias.get(alias);
            Assertions.assertNotNull(tableName, alias + "." + column + " refers to no gateway table: " + sql);
            Assertions.assertTrue(ALLOWED.get(tableName).contains(column),
                    tableName + "." + column + " is not in the provider SQL: " + sql);
        }
    }
}
