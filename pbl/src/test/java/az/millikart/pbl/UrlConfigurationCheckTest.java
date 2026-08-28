package az.millikart.pbl;

import az.millikart.pbl.config.UrlConfigurationCheck;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

// P1-10: стартовая проверка трёх адресов, зависящих от окружения. Чистый JUnit, без Spring: класс
// делает всю работу в конструкторе, поэтому создание объекта и есть весь тест. Лог снимается
// logback-овским ListAppender, потому что проверяемый контракт — ровно «предупреждать, но не
// отказывать» (Р-17): HTTP-адрес эквайера обязан дать WARN и не бросить исключение.
class UrlConfigurationCheckTest {

    private static final String HTTPS_BASE = "https://pay.example.com/";
    private static final String HTTPS_GATEWAY = "https://gateway.txpg.example.com/pay";
    private static final String HTTPS_API = "https://api.txpg.example.com/pay";

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void captureLog() {
        logger = (Logger) LoggerFactory.getLogger(UrlConfigurationCheck.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void releaseLog() {
        logger.detachAppender(appender);
        appender.stop();
    }

    // 1. Всё по HTTPS: тишина

    @Test
    void allHttps_noWarning() {
        new UrlConfigurationCheck(HTTPS_BASE, HTTPS_GATEWAY, HTTPS_API);

        Assertions.assertTrue(warnings().isEmpty(), () -> "expected no WARN, got: " + warnings());
    }

    // 2, 3. Голый HTTP до эквайера: одно предупреждение, без отказа

    @Test
    void apiBaseUrlOverHttp_exactlyOneWarningNamingAddressAndVariable() {
        String insecureApi = "http://api.txpg.example.com:8000/";

        new UrlConfigurationCheck(HTTPS_BASE, HTTPS_GATEWAY, insecureApi);

        List<ILoggingEvent> warnings = warnings();
        Assertions.assertEquals(1, warnings.size(), () -> "expected exactly one WARN, got: " + warnings);
        String message = warnings.get(0).getFormattedMessage();
        Assertions.assertTrue(message.contains(insecureApi), "the WARN must name the address: " + message);
        Assertions.assertTrue(message.contains("PBL_PROVIDER_API_BASE_URL"),
                "the WARN must name the environment variable: " + message);
        Assertions.assertTrue(message.contains("Basic authentication"),
                "the WARN must say what travels over the channel: " + message);
    }

    @Test
    void gatewayBaseUrlOverHttp_warns() {
        String insecureGateway = "http://gateway.txpg.example.com:8083/";

        new UrlConfigurationCheck(HTTPS_BASE, insecureGateway, HTTPS_API);

        List<ILoggingEvent> warnings = warnings();
        Assertions.assertEquals(1, warnings.size(), () -> "expected exactly one WARN, got: " + warnings);
        String message = warnings.getFirst().getFormattedMessage();
        Assertions.assertTrue(message.contains(insecureGateway), "the WARN must name the address: " + message);
        Assertions.assertTrue(message.contains("PBL_PROVIDER_GATEWAY_BASE_URL"),
                "the WARN must name the environment variable: " + message);
    }

    // 4, 5. Голый HTTP у публичного адреса: локальный запуск в порядке, остальное предупреждает

    @Test
    void baseUrlOverHttpOnLocalhost_noWarning() {
        new UrlConfigurationCheck("http://localhost:8080/", HTTPS_GATEWAY, HTTPS_API);

        Assertions.assertTrue(warnings().isEmpty(), () -> "expected no WARN for localhost, got: " + warnings());
    }

    @Test
    void baseUrlOverHttpOnLoopbackAddress_noWarning() {
        new UrlConfigurationCheck("http://127.0.0.1:8080/", HTTPS_GATEWAY, HTTPS_API);

        Assertions.assertTrue(warnings().isEmpty(), () -> "expected no WARN for 127.0.0.1, got: " + warnings());
    }

    @Test
    void baseUrlOverHttpOnIpv6Loopback_noWarning() {
        new UrlConfigurationCheck("http://[::1]:8080/", HTTPS_GATEWAY, HTTPS_API);

        Assertions.assertTrue(warnings().isEmpty(), () -> "expected no WARN for [::1], got: " + warnings());
    }

    @Test
    void baseUrlOverHttpOnRealHost_warns() {
        String insecureBase = "http://pay.example.com/";

        new UrlConfigurationCheck(insecureBase, HTTPS_GATEWAY, HTTPS_API);

        List<ILoggingEvent> warnings = warnings();
        Assertions.assertEquals(1, warnings.size(), () -> "expected exactly one WARN, got: " + warnings);
        String message = warnings.getFirst().getFormattedMessage();
        Assertions.assertTrue(message.contains(insecureBase), "the WARN must name the address: " + message);
        Assertions.assertTrue(message.contains("PBL_BASE_URL"), "the WARN must name the variable: " + message);
    }

    // 6, 7, 8. Значения без рабочего прочтения: отказ стартовать

    @Test
    void emptyBaseUrl_refusesToStartNamingTheVariable() {
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class,
                () -> new UrlConfigurationCheck("", HTTPS_GATEWAY, HTTPS_API));

        Assertions.assertTrue(thrown.getMessage().contains("PBL_BASE_URL"), thrown.getMessage());
        Assertions.assertTrue(thrown.getMessage().contains("How to fix"), thrown.getMessage());
    }

    @Test
    void relativeBaseUrl_refusesToStart() {
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class,
                () -> new UrlConfigurationCheck("/pay", HTTPS_GATEWAY, HTTPS_API));

        Assertions.assertTrue(thrown.getMessage().contains("PBL_BASE_URL"), thrown.getMessage());
        Assertions.assertTrue(thrown.getMessage().contains("/pay"), thrown.getMessage());
    }

    @Test
    void nonHttpScheme_refusesToStart() {
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class,
                () -> new UrlConfigurationCheck("ftp://pay.example.com/", HTTPS_GATEWAY, HTTPS_API));

        Assertions.assertTrue(thrown.getMessage().contains("PBL_BASE_URL"), thrown.getMessage());
        Assertions.assertTrue(thrown.getMessage().contains("ftp"), thrown.getMessage());
    }

    @Test
    void unparseableProviderAddress_refusesToStartNamingItsVariable() {
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class,
                () -> new UrlConfigurationCheck(HTTPS_BASE, HTTPS_GATEWAY, "http://api txpg.example.com/"));

        Assertions.assertTrue(thrown.getMessage().contains("PBL_PROVIDER_API_BASE_URL"), thrown.getMessage());
    }

    // Потребители берут значение без trim, поэтому хвостовой пробел прошёл бы проверку по
    // обрезанной строке и превратил бы каждый URL эквайера в https://host/%20/order — то самое
    // «стартует, но платежи не идут», ради чего проверка и есть. Значит, пробел — видимый отказ.
    @Test
    void surroundingWhitespace_refusesToStartAndShowsIt() {
        IllegalStateException trailingSpace = Assertions.assertThrows(IllegalStateException.class,
                () -> new UrlConfigurationCheck(HTTPS_BASE, HTTPS_GATEWAY, "https://api.txpg.example.com/ "));
        Assertions.assertTrue(trailingSpace.getMessage().contains("PBL_PROVIDER_API_BASE_URL"), trailingSpace.getMessage());
        Assertions.assertTrue(trailingSpace.getMessage().contains("whitespace"), trailingSpace.getMessage());

        IllegalStateException crlf = Assertions.assertThrows(IllegalStateException.class,
                () -> new UrlConfigurationCheck("https://pay.example.com/\r\n", HTTPS_GATEWAY, HTTPS_API));
        Assertions.assertTrue(crlf.getMessage().contains("PBL_BASE_URL"), crlf.getMessage());
        Assertions.assertTrue(crlf.getMessage().contains("\\r\\n"), "the line break must be visible: " + crlf.getMessage());
    }

    // java.net.URI проглатывает https://ВАШ_ДОМЕН/ (плейсхолдер из инструкции) и https://my_host/
    // без синтаксической ошибки, но и без хоста — сообщение обязано указать на хост, а не заявлять,
    // что у URL нет схемы.
    @Test
    void unreplacedPlaceholderHost_refusesToStartNamingTheHostPart() {
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class,
                () -> new UrlConfigurationCheck("https://ВАШ_ДОМЕН/", HTTPS_GATEWAY, HTTPS_API));

        Assertions.assertTrue(thrown.getMessage().contains("PBL_BASE_URL"), thrown.getMessage());
        Assertions.assertTrue(thrown.getMessage().contains("host name"), thrown.getMessage());
        Assertions.assertTrue(thrown.getMessage().contains("ВАШ_ДОМЕН"), thrown.getMessage());
    }

    @Test
    void emptyGatewayAddress_refusesToStartNamingItsVariable() {
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class,
                () -> new UrlConfigurationCheck(HTTPS_BASE, "   ", HTTPS_API));

        Assertions.assertTrue(thrown.getMessage().contains("PBL_PROVIDER_GATEWAY_BASE_URL"), thrown.getMessage());
    }

    private List<ILoggingEvent> warnings() {
        return appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .toList();
    }
}
