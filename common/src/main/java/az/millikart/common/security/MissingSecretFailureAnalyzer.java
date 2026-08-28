package az.millikart.common.security;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.diagnostics.FailureAnalyzer;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

// Превращает отсутствующую переменную окружения в инструкцию, а не в стектрейс: секреты и адреса
// намеренно объявлены без значений по умолчанию (P0-5, P1-10), поэтому сервис умирает раньше, чем
// поднимется хоть один наш бин, и JwtProvider объясниться не успевает. Имя класса шире секретов:
// анализируется «обязательная переменная без дефолта», что бы она ни защищала.
public class MissingSecretFailureAnalyzer implements FailureAnalyzer, EnvironmentAware {

    private static final String DB_PASSWORD_PROPERTY = "spring.datasource.password";

    private static final String SAME_KEY_EVERYWHERE =
            "The same value must be set for all three services (auth, directory, pbl): auth signs the "
                    + "token, directory and pbl verify it, and a mismatch turns every request into 401.";

    private static final String JWT_SECRET_ACTION =
            "Generate a signing key and export it before starting the service:\n"
                    + "\texport JWT_SECRET=\"$(openssl rand -base64 48)\"\n"
                    + SAME_KEY_EVERYWHERE + "\n"
                    + "See .env.example and deployment_guide.md, \"Первый запуск и ротация ключа\".";

    private static final String DB_PASSWORD_ACTION =
            """
                    Export the database password before starting the service:
                    \texport DB_PASSWORD='<password of the PostgreSQL user>'
                    DB_URL and DB_USERNAME have defaults for local runs; the password has none on purpose.
                    See .env.example.""";

    private static final String PBL_API_TOKEN_ACTION =
            """
                    Either leave the static fallback token off (recommended):
                    \texport PBL_API_TOKEN_ENABLED=false
                    or give it a value:
                    \texport PBL_API_TOKEN="$(openssl rand -base64 32)"
                    See .env.example.""";

    private static final String PBL_BASE_URL_ACTION =
            "Export the public address of the pbl service — the one the payer's browser opens and the "
                    + "acquirer returns the payer to after paying:\n"
                    + "\texport PBL_BASE_URL='https://<your domain>/'\n"
                    + "For a local run: export PBL_BASE_URL='http://localhost:8080/'\n"
                    + "See .env.example and deployment_guide.md, section 8.3.";

    private static final String PBL_PROVIDER_GATEWAY_BASE_URL_ACTION =
            """
                    Export the address of the acquiring (TXPG) gateway that hosts the payment page:
                    \texport PBL_PROVIDER_GATEWAY_BASE_URL='https://<gateway host>:<port>/'
                    The value comes from MilliKart and differs between the test stand and production.
                    See .env.example and deployment_guide.md, section 8.3.""";

    private static final String PBL_PROVIDER_API_BASE_URL_ACTION =
            """
                    Export the address of the acquirer's e-commerce API (order status, capture, refund):
                    \texport PBL_PROVIDER_API_BASE_URL='https://<api host>:<port>/'
                    The value comes from MilliKart and differs between the test stand and production.
                    See .env.example and deployment_guide.md, section 8.3.""";

    private static final Map<String, String> ACTIONS_BY_VARIABLE = new LinkedHashMap<>();

    // Переменные-адреса, а не секреты: механизм тот же (нет дефолта, тот же отказ), причина другая,
    // поэтому description() объясняет их отдельно.
    private static final Set<String> ADDRESS_VARIABLES = Set.of(
            "PBL_BASE_URL", "PBL_PROVIDER_GATEWAY_BASE_URL", "PBL_PROVIDER_API_BASE_URL");

    static {
        ACTIONS_BY_VARIABLE.put("JWT_SECRET", JWT_SECRET_ACTION);
        ACTIONS_BY_VARIABLE.put("DB_PASSWORD", DB_PASSWORD_ACTION);
        ACTIONS_BY_VARIABLE.put("PBL_API_TOKEN", PBL_API_TOKEN_ACTION);
        ACTIONS_BY_VARIABLE.put("PBL_BASE_URL", PBL_BASE_URL_ACTION);
        ACTIONS_BY_VARIABLE.put("PBL_PROVIDER_GATEWAY_BASE_URL", PBL_PROVIDER_GATEWAY_BASE_URL_ACTION);
        ACTIONS_BY_VARIABLE.put("PBL_PROVIDER_API_BASE_URL", PBL_PROVIDER_API_BASE_URL_ACTION);
    }

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public FailureAnalysis analyze(Throwable failure) {
        FailureAnalysis fromPlaceholder = analyzeUnresolvedPlaceholder(failure);
        if (fromPlaceholder != null) {
            return fromPlaceholder;
        }
        return analyzeUnboundDatabasePassword(failure);
    }

    // Путь @Value (так читаются JWT_SECRET и PBL_API_TOKEN): нерезолвнутый плейсхолдер сразу даёт
    // IllegalArgumentException, и переменную называет само сообщение.
    private FailureAnalysis analyzeUnresolvedPlaceholder(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message == null || !message.contains("Could not resolve placeholder")) {
                continue;
            }
            for (Map.Entry<String, String> entry : ACTIONS_BY_VARIABLE.entrySet()) {
                if (message.contains("'" + entry.getKey() + "'")) {
                    return new FailureAnalysis(description(entry.getKey()), entry.getValue(), failure);
                }
            }
        }
        return null;
    }

    // Путь биндера @ConfigurationProperties (так читается spring.datasource.password) плейсхолдеры
    // терпит: неустановленный DB_PASSWORD уходит в драйвер литералом "${DB_PASSWORD}", и сервис
    // умирает много позже на ошибке аутентификации БД. Поэтому распознаём по окружению, а не по
    // исключению: пароль, который всё ещё читается как ${...}, не был подставлен.
    private FailureAnalysis analyzeUnboundDatabasePassword(Throwable failure) {
        if (environment == null || !hasUnresolvedDatabasePassword()) {
            return null;
        }
        return new FailureAnalysis(
                description("DB_PASSWORD") + "\nThe service was started with the literal text \"${DB_PASSWORD}\" "
                        + "as the database password, so this connection could never have succeeded.",
                DB_PASSWORD_ACTION,
                failure);
    }

    private boolean hasUnresolvedDatabasePassword() {
        try {
            String password = environment.getProperty(DB_PASSWORD_PROPERTY);
            return password != null && password.contains("${DB_PASSWORD}");
        } catch (IllegalArgumentException e) {
            // Environment.getProperty сам отказался резолвить плейсхолдер — вывод тот же.
            return String.valueOf(e.getMessage()).contains("DB_PASSWORD");
        }
    }

    private static String description(String variable) {
        if (ADDRESS_VARIABLES.contains(variable)) {
            return "The environment variable " + variable + " is not set. It has no default value: "
                    + "an address with a default is a production that silently sends payers to localhost "
                    + "or payments to the test acquirer — the service would start, and only the money "
                    + "would not move.";
        }
        return "The environment variable " + variable + " is not set. It has no default value: "
                + "a secret with a default is how the previous signing key ended up in the repository.";
    }
}
