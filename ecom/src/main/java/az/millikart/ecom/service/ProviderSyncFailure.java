package az.millikart.ecom.service;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessResourceFailureException;

final class ProviderSyncFailure {

    private ProviderSyncFailure() {
    }

    // Нет соединения — «unavailable»; запрос дошёл, но база его отвергла (ORA-00942 и т. п.) — «query
    // failed». Текст — первая строка самой глубокой причины: у Oracle вторая — ссылка на справку.
    static String reason(RuntimeException e) {
        String kind = kind(e);
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return kind + ": " + cause.getClass().getSimpleName();
        }
        return kind + ": " + message.strip().lines().findFirst().orElse(message).strip();
    }

    static String kind(RuntimeException e) {
        return e instanceof DataAccessResourceFailureException ? "gateway unavailable" : "gateway query failed";
    }
}
