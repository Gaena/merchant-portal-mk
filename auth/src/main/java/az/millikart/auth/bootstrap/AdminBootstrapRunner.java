package az.millikart.auth.bootstrap;

import az.millikart.auth.domain.User;
import az.millikart.auth.repository.UserRepository;
import az.millikart.common.audit.AuditAction;
import az.millikart.common.audit.AuditEntity;
import az.millikart.common.audit.AuditEvent;
import az.millikart.common.security.Role;
import az.millikart.common.validation.PasswordConstraintValidator;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// Создаёт первого SYSTEM_ADMIN новой установки из переменных окружения — вместо админа, которого
// сеял Liquibase-changeset с паролем рядом с хэшем (P0-6): миграция одинакова на всех установках
// и видна всем с доступом к репозиторию. Только при явном auth.bootstrap.enabled=true И на пустой
// таблице users: удалённого админа не воскресит, существующего не перезапишет.
@Component
@ConditionalOnProperty(name = "auth.bootstrap.enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    // Логин проверяет username как email (LoginRequest), поэтому админ, созданный с чем-то другим,
    // не смог бы войти никогда. Намеренно слабо — проверка на очевидную опечатку, не на RFC 5322.
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private static final String HOW_TO_FIX = """
            How to fix: start auth once with both variables set, then switch AUTH_BOOTSTRAP_ENABLED back to false:
              export AUTH_BOOTSTRAP_ENABLED=true
              export BOOTSTRAP_ADMIN_USERNAME='admin@your-company.az'
              export BOOTSTRAP_ADMIN_PASSWORD='<at least 12 chars: upper, lower, digit, special>'
            The password must satisfy the same policy as every other account (PCI-DSS v4.0).""";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final String username;
    private final String password;

    public AdminBootstrapRunner(UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                ApplicationEventPublisher eventPublisher,
                                @Value("${BOOTSTRAP_ADMIN_USERNAME:}") String username,
                                @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        long existingUsers = userRepository.count();
        if (existingUsers > 0) {
            log.info("Admin bootstrap skipped: the users table already holds {} row(s). "
                    + "Set AUTH_BOOTSTRAP_ENABLED=false to stop running this check on every start.", existingUsers);
            return;
        }

        String cleanUsername = username == null ? "" : username.trim().toLowerCase();
        validate(cleanUsername);

        User admin = User.builder()
                .username(cleanUsername)
                .passwordHash(passwordEncoder.encode(password))
                .fullName("System Administrator")
                .role(Role.SYSTEM_ADMIN.name())
                .companyId(null)
                .status("ACTIVE")
                .build();
        admin = userRepository.save(admin);

        // Первый полнопривилегированный аккаунт установки попадает в журнал наравне с прочими
        // (P2-14). Залогинен никто не был, поэтому актор — сам сервис. Идёт вне транзакции, и
        // AuditLogWriter это учитывает: fallbackExecution=true пишет запись сразу, не дожидаясь
        // коммита, которого не будет.
        eventPublisher.publishEvent(AuditEvent.of(AuditEntity.USER, admin.getId().toString(), AuditAction.CREATE,
                "system", null,
                "Admin bootstrap created the first SYSTEM_ADMIN " + admin.getUsername()));

        // WARN, чтобы создание полнопривилегированного аккаунта было видно в консоли ручного пуска.
        // Пароль не логируется никогда.
        log.warn("Admin bootstrap created SYSTEM_ADMIN '{}' (id={}). "
                        + "Set AUTH_BOOTSTRAP_ENABLED=false and unset BOOTSTRAP_ADMIN_PASSWORD before the next start.",
                admin.getUsername(), admin.getId());
    }

    // Лучше не стартовать, чем создать слабого или неработоспособного администратора. Правила
    // пароля здесь не переписаны: единственное определение политики — PasswordConstraintValidator,
    // тот же, что CreateUserRequest применяет ко всем прочим аккаунтам.
    private void validate(String cleanUsername) {
        if (cleanUsername.isEmpty() || password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "auth.bootstrap.enabled is true but BOOTSTRAP_ADMIN_USERNAME and/or BOOTSTRAP_ADMIN_PASSWORD "
                            + "is not set, so there is no administrator to create.\n" + HOW_TO_FIX);
        }
        if (!EMAIL_PATTERN.matcher(cleanUsername).matches()) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_USERNAME must be an email address ('" + cleanUsername + "' is not): "
                            + "login rejects anything else, so this account could never sign in.\n" + HOW_TO_FIX);
        }
        if (!new PasswordConstraintValidator().isValid(password, null)) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_PASSWORD does not satisfy the password policy: at least 12 characters "
                            + "with an uppercase letter, a lowercase letter, a digit and a special character "
                            + "(PCI-DSS v4.0). The administrator account is not allowed a weaker password than "
                            + "the accounts it creates.\n" + HOW_TO_FIX);
        }
    }
}
