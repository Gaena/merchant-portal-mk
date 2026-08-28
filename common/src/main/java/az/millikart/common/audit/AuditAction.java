package az.millikart.common.audit;

import java.util.Set;

// Словарь действий журнала, вторая половина AuditEntity (P3-2) — там же, почему константы, а не
// enum. ACCESS в словаре нет намеренно: так pbl называл отказ, который directory писал как READ, и
// поиск по одному не находил другого. Не возвращать его — новый вид отказа пишется тем действием,
// в котором отказали. BLOCK/UNBLOCK — свои действия у каждой сущности со статусом, не текст в UPDATE.
public final class AuditAction {

    public static final String CREATE = "CREATE";
    public static final String READ = "READ";
    public static final String UPDATE = "UPDATE";
    public static final String DELETE = "DELETE";
    public static final String LIST = "LIST";
    public static final String BLOCK = "BLOCK";
    public static final String UNBLOCK = "UNBLOCK";
    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";
    public static final String LOCKOUT = "LOCKOUT";
    public static final String RATE_LIMIT = "RATE_LIMIT";
    public static final String TOKEN_REUSE = "TOKEN_REUSE";
    public static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";
    public static final String CAPTURE = "CAPTURE";
    public static final String REFUND = "REFUND";
    public static final String CANCEL = "CANCEL";

    private static final Set<String> KNOWN = Set.of(
            CREATE, READ, UPDATE, DELETE, LIST, BLOCK, UNBLOCK,
            LOGIN, LOGOUT, LOCKOUT, RATE_LIMIT, TOKEN_REUSE, PASSWORD_CHANGE,
            CAPTURE, REFUND, CANCEL);

    private AuditAction() {
    }

    static boolean isKnown(String value) {
        return value != null && KNOWN.contains(value);
    }
}
