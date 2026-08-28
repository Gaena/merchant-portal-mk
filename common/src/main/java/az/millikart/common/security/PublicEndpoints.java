package az.millikart.common.security;

import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

// Единственное объявление путей, доступных без токена, и потому граница безопасности: добавить
// сюда путь — значит открыть его миру без токена. Список один на JwtAuthFilter и SecurityConfig
// во всех трёх сервисах, чтобы они не разъехались (P1-1). Чего здесь нет — требует токена:
// SecurityConfig заканчивает правила на anyRequest().authenticated().
public final class PublicEndpoints {

    // Всё под /api/v1/auth — вход, токена на этот момент ещё нет. Два других — половина
    // плательщика: анонимный держатель карты, пришедший по ссылке, а не пользователь портала.
    // В шаблоне payment-links звёздочка покрывает ровно один сегмент под id, как в реальном
    // маппинге "/{id}/open"; прежняя проверка endsWith("/open") ловила путь любой глубины.
    public static final String[] PUBLIC_API = {
            "/api/v1/auth/**",
            "/api/v1/payment-links/*/open",
            "/api/v1/payment-links/redirect/**"
    };

    // Служебные пути защищены не токеном, а привязкой management-порта к 127.0.0.1
    // (management.server в application.yaml): снаружи машины они недостижимы, а у liveness-проб
    // нет учётных данных. На основном порту разрешены на случай возврата actuator на server.port;
    // по умолчанию там ничего не смаплено и основной порт отвечает 404.
    public static final String[] INFRASTRUCTURE = {
            "/actuator/**"
    };

    // Пути springdoc, в отличие от двух групп выше, разрешены условно — только при
    // springdoc.api-docs.enabled (по умолчанию выключен, включается для приёмочных тестов).
    // С выключенным флагом springdoc не регистрирует обработчиков, и путей просто нет.
    public static final String[] SWAGGER = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml"
    };

    // AntPathMatcher потокобезопасен и кэширует разобранные паттерны — хватает одного экземпляра.
    private static final PathMatcher MATCHER = new AntPathMatcher();

    private PublicEndpoints() {
    }

    // SWAGGER сюда намеренно не входит: он зависит от флага конфигурации, а метод, отвечающий
    // «публичный» безусловно, был бы неверен при выключенном флаге.
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
