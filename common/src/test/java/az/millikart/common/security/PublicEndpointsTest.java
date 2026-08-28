package az.millikart.common.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Список публичных путей — граница безопасности, поэтому у него свой юнит-тест: эти проверки
// путь за путём фиксируют, что достижимо без токена.
class PublicEndpointsTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/auth/login",
            "/api/v1/auth/anything",
            "/api/v1/payment-links/3f7c1a2e-0000-0000-0000-000000000001/open",
            "/api/v1/payment-links/redirect/3f7c1a2e-0000-0000-0000-000000000002",
            "/actuator/health",
            "/actuator/metrics"
    })
    void isPublic_acceptsTheDeclaredPaths(String path) {
        assertTrue(PublicEndpoints.isPublic(path));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/users",
            "/api/v1/companies",
            "/api/v1/terminals",
            "/api/v1/audit-logs",
            "/api/v1/payment-links",
            "/api/v1/payment-links/3f7c1a2e-0000-0000-0000-000000000001",
            "/api/v1/transactions",
            "/api/v1/transactions/3f7c1a2e-0000-0000-0000-000000000003/status",
            "/some/unmapped/path",
            "/",
            ""
    })
    void isPublic_rejectsEverythingElse(String path) {
        assertFalse(PublicEndpoints.isPublic(path));
    }

    // Ужесточение из P1-1. Старый фильтр принимал любой путь под /api/v1/payment-links/,
    // оканчивающийся на /open, на любой глубине; шаблон теперь допускает ровно один сегмент,
    // объявленный в маппинге.
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/payment-links/a/b/open",
            "/api/v1/payment-links/a/b/c/open",
            "/api/v1/payment-links/open",
            "/api/v1/payment-links/1/open/2"
    })
    void isPublic_rejectsOpenAtAnyOtherDepth(String path) {
        assertFalse(PublicEndpoints.isPublic(path));
    }

    // Префикс, лишь похожий на auth-овский, — это не он.
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/authentication",
            "/api/v1/auth-admin/login",
            "/api/v2/auth/login"
    })
    void isPublic_rejectsLookalikePrefixes(String path) {
        assertFalse(PublicEndpoints.isPublic(path));
    }

    @Test
    void isPublic_nullPathIsNotPublic() {
        assertFalse(PublicEndpoints.isPublic(null));
    }

    // Springdoc намеренно вне isPublic: он зависит от флага, которого матчер не видит.
    @ParameterizedTest
    @ValueSource(strings = {
            "/swagger-ui.html",
            "/swagger-ui/index.html",
            "/v3/api-docs",
            "/v3/api-docs/swagger-config",
            "/v3/api-docs.yaml"
    })
    void isSwagger_recognisesSpringdocPaths(String path) {
        assertTrue(PublicEndpoints.isSwagger(path));
        assertFalse(PublicEndpoints.isPublic(path));
    }

    @Test
    void isSwagger_rejectsUnrelatedPaths() {
        assertFalse(PublicEndpoints.isSwagger("/api/v1/users"));
        assertFalse(PublicEndpoints.isSwagger(null));
    }
}
