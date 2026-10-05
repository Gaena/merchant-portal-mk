package az.millikart.pbl.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Три адреса без дефолта (P1-10): неверный адрес не роняет сервис, а уводит плательщика и деньги не
// туда. Поэтому пустой или нечитаемый адрес — отказ старта, plain HTTP — предупреждение (Р-17).
@Component
public class UrlConfigurationCheck {

    private static final Logger log = LoggerFactory.getLogger(UrlConfigurationCheck.class);

    static final String BASE_URL_VARIABLE = "PBL_BASE_URL";
    static final String GATEWAY_BASE_URL_VARIABLE = "PROVIDER_GATEWAY_BASE_URL";
    static final String API_BASE_URL_VARIABLE = "PROVIDER_API_BASE_URL";

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    // Plain HTTP на них — локальный запуск. URI.getHost() отдаёт IPv6 со скобками — отсюда "[::1]".
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    private static final String FRAME =
            "==============================================================================";

    private static final String HOW_TO_FIX = """
            How to fix:
              Export an absolute http(s) URL before starting the service, for example:
                export PBL_BASE_URL='https://pay.example.com/'
                export PROVIDER_GATEWAY_BASE_URL='https://gateway.acquirer.example:8083/'
                export PROVIDER_API_BASE_URL='https://api.acquirer.example:8000/'
              None of the three has a default value on purpose. PBL_BASE_URL is sent to the acquirer
              as the address the payer is returned to after paying; the two provider addresses decide
              which acquirer receives the payments. A wrong value does not stop the service — it stops
              the payments — so an unset value stops the service instead.
              See .env.example and project_docs/guides/deployment_guide.md, section 8.3.""";

    public UrlConfigurationCheck(
            @Value("${pbl.base-url}") String baseUrl,
            @Value("${pbl.provider.gateway-base-url}") String gatewayBaseUrl,
            @Value("${pbl.provider.api-base-url}") String apiBaseUrl) {
        // Сначала валидация всех трёх: отказ старта должен прозвучать раньше предупреждений.
        URI base = requireHttpUrl(baseUrl, BASE_URL_VARIABLE, "pbl.base-url", "https://pay.example.com/");
        URI gateway = requireHttpUrl(gatewayBaseUrl, GATEWAY_BASE_URL_VARIABLE, "pbl.provider.gateway-base-url",
                "https://gateway.acquirer.example:8083/");
        URI api = requireHttpUrl(apiBaseUrl, API_BASE_URL_VARIABLE, "pbl.provider.api-base-url",
                "https://api.acquirer.example:8000/");

        warnIfProviderNotHttps(gateway, GATEWAY_BASE_URL_VARIABLE, "gateway (payment page, createEcomOrder)");
        warnIfProviderNotHttps(api, API_BASE_URL_VARIABLE, "e-commerce API (exec-tran, order status)");
        warnIfBaseUrlNotHttps(base);
    }

    // Не тримить: потребители (@Value, OpenLinkService, TxpgAcquiringClient) берут значение как есть, и
    // пробел или CR из env-файла превратил бы каждый URL в https://host/%20/order.
    private static URI requireHttpUrl(String value, String variable, String property, String example) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "The environment variable " + variable + " (property " + property + ") is empty. "
                            + "The service cannot start without it.\n"
                            + "Example of a correct value: " + example + "\n" + HOW_TO_FIX);
        }
        if (!value.equals(value.strip())) {
            throw new IllegalStateException(
                    "The environment variable " + variable + " (property " + property + ") has leading or "
                            + "trailing whitespace: got \"" + visible(value) + "\". Remove it — the value is used "
                            + "exactly as set, and a stray space or line break breaks every URL built from it "
                            + "(check for CRLF line endings in the env file).\n"
                            + "Example of a correct value: " + example + "\n" + HOW_TO_FIX);
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(
                    "The environment variable " + variable + " (property " + property + ") is not a valid URL: "
                            + "got \"" + value + "\" (" + e.getReason() + ").\n"
                            + "Example of a correct value: " + example + "\n" + HOW_TO_FIX, e);
        }
        if (!uri.isAbsolute()) {
            throw new IllegalStateException(
                    "The environment variable " + variable + " (property " + property + ") must be an absolute URL "
                            + "with a scheme and a host: got \"" + value + "\".\n"
                            + "Example of a correct value: " + example + "\n" + HOW_TO_FIX);
        }
        if (uri.getHost() == null) {
            // URI разбирает "https://ВАШ_ДОМЕН/" и "https://my_host/", но хост не читает: это плейсхолдер
            // или подчёркивание, и «нужен хост» противоречило бы тому, что видит оператор.
            throw new IllegalStateException(
                    "The environment variable " + variable + " (property " + property + ") has a host part that is "
                            + "not a valid host name: got \"" + value + "\" (host part \"" + uri.getRawAuthority()
                            + "\"). A host name consists of ASCII letters, digits, '-' and '.' only; if this is a "
                            + "placeholder from the deployment guide, replace it with the real domain.\n"
                            + "Example of a correct value: " + example + "\n" + HOW_TO_FIX);
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            throw new IllegalStateException(
                    "The environment variable " + variable + " (property " + property + ") must use http or https: "
                            + "got \"" + value + "\" (scheme \"" + uri.getScheme() + "\").\n"
                            + "Example of a correct value: " + example + "\n" + HOW_TO_FIX);
        }
        return uri;
    }

    private static String visible(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }

    private static boolean isHttps(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme());
    }

    // Предупреждение, а не отказ (Р-17): HTTPS даёт стенд эквайера. Флага «разрешить небезопасно» нет
    // намеренно — его выставили бы один раз и забыли.
    private static void warnIfProviderNotHttps(URI uri, String variable, String role) {
        if (isHttps(uri)) {
            return;
        }
        log.warn("""
                        
                        {}
                          WARNING: the acquirer address is not HTTPS
                          {} = {}
                          Role: {}.
                          Every request to this address carries Basic authentication with the company login and
                          password in the clear, and the order data with it. Anyone on the network path can read
                          and replay them. The service starts anyway: HTTPS is provided by the acquirer's stand,
                          not by this service. Ask MilliKart for an HTTPS endpoint and point {}
                          at it — nothing else needs to change.
                        {}""",
                FRAME, variable, uri, role, variable, FRAME);
    }

    private static void warnIfBaseUrlNotHttps(URI uri) {
        if (isHttps(uri)) {
            return;
        }
        if (LOCAL_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))) {
            return;
        }
        log.warn("""
                        
                        {}
                          WARNING: the public address of this service is not HTTPS
                          {} = {}
                          This is the address the payer's browser opens and the address the acquirer returns the
                          payer to after paying (hppRedirectUrl). Over plain HTTP the payer's session and the
                          payment reference in the return redirect are readable on the way, and the acquirer
                          may refuse a non-HTTPS return address altogether. In production put the service behind
                          HTTPS (project_docs/guides/deployment_guide.md, section 12) and set {} to the https:// address.
                          Only http://localhost, http://127.0.0.1 and http://[::1] are exempt from this warning.
                        {}""",
                FRAME, BASE_URL_VARIABLE, uri, BASE_URL_VARIABLE, FRAME);
    }
}
