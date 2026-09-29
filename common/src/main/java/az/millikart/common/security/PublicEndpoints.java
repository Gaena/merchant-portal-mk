package az.millikart.common.security;

import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

// Единственный список путей без токена — граница безопасности: добавить путь — открыть его миру.
// Один на JwtAuthFilter и SecurityConfig во всех сервисах, чтобы слои не разъехались (P1-1).
public final class PublicEndpoints {

    // /api/v1/auth — вход, токена ещё нет; payment-links — плательщик по ссылке, не пользователь
    // портала. * в шаблоне open — ровно один сегмент, как в маппинге /{id}/open.
    public static final String[] PUBLIC_API = {
            "/api/v1/auth/**",
            "/api/v1/payment-links/*/open",
            "/api/v1/payment-links/redirect/**"
    };

    // Защищены не токеном, а привязкой management-порта к 127.0.0.1: снаружи недостижимы, у проб нет
    // учётных данных. На рабочем порту разрешены на случай возврата туда actuator; сейчас там 404.
    public static final String[] INFRASTRUCTURE = {
            "/actuator/**"
    };

    // Разрешены только при springdoc.api-docs.enabled (по умолчанию выключен).
    public static final String[] SWAGGER = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml"
    };

    // Потокобезопасен — хватает одного экземпляра.
    private static final PathMatcher MATCHER = new AntPathMatcher();

    private PublicEndpoints() {
    }

    // SWAGGER не входит намеренно: он публичен только при включённом флаге.
    public static boolean isPublic(String path) {
        return matchesAny(PUBLIC_API, path) || matchesAny(INFRASTRUCTURE, path);
    }

    // Осмысленно только вместе с проверкой флага springdoc.api-docs.enabled.
    public static boolean isSwagger(String path) {
        return matchesAny(SWAGGER, path);
    }

    private static boolean matchesAny(String[] patterns, String path) {
        if (path == null) {
            return false;
        }
        for (String pattern : patterns) {
            if (MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
