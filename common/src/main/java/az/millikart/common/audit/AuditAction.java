package az.millikart.common.audit;

import java.util.Set;

// Словарь действий журнала (P3-2); почему не enum — в AuditEntity. ACCESS нет намеренно: отказ
// пишется тем действием, в котором отказали, иначе поиск по одному не находит другого.
// BLOCK/UNBLOCK — свои действия, а не текст в UPDATE.
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
    // Неизвестный исход денежной операции разрешён администратором после сверки с провайдером (Р-123).
    public static final String RESOLVE = "RESOLVE";

    private static final Set<String> KNOWN = Set.of(
            CREATE, READ, UPDATE, DELETE, LIST, BLOCK, UNBLOCK,
            LOGIN, LOGOUT, LOCKOUT, RATE_LIMIT, TOKEN_REUSE, PASSWORD_CHANGE,
            CAPTURE, REFUND, CANCEL, RESOLVE);

    private AuditAction() {
    }

    static boolean isKnown(String value) {
        return value != null && KNOWN.contains(value);
    }
}
