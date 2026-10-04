package az.millikart.ecom.service;

import az.millikart.ecom.domain.ProviderLogin;
import az.millikart.ecom.repository.ProviderLoginRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// Слепок заменяется целиком (Р-94). Неудачный и пустой опрос не применяются: иначе недоступный шлюз
// запретил бы заводить компании и терминалы, а выписка опустела бы у всех (Р-96, Р-97).
@Service
public class ProviderLoginSyncService {

    private static final Logger log = LoggerFactory.getLogger(ProviderLoginSyncService.class);

    private final ProviderLoginSource source;
    private final ProviderLoginRepository repository;
    private final TransactionTemplate transactionTemplate;

    public ProviderLoginSyncService(ProviderLoginSource source, ProviderLoginRepository repository,
                                    PlatformTransactionManager transactionManager) {
        this.source = source;
        this.repository = repository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // logins — разных логинов в выгрузке, links — строк-связей с мерчантом.
    public record SyncOutcome(boolean applied, int logins, int links, String skippedBecause) {

        public static SyncOutcome skipped(String reason) {
            return new SyncOutcome(false, 0, 0, reason);
        }
    }

    // Проходы — по одному, замок до коммита: два разом стирали слепок друг друга до вставки, и связи
    // задваивались (ECOM-SYNC-RACE, Р-119). Поэтому транзакция внутри, а не @Transactional на методе.
    public synchronized SyncOutcome sync() {
        List<ProviderLoginSource.ProviderLoginRow> rows;
        try {
            rows = source.fetchMultiMerchantLogins();
        } catch (RuntimeException e) {
            String reason = ProviderSyncFailure.reason(e);
            log.error("Provider login sync skipped: {}. The previous snapshot is kept as is.", reason, e);
            return SyncOutcome.skipped(reason);
        }
        if (rows == null || rows.isEmpty()) {
            log.error("Provider login sync skipped: the gateway returned no multimerchant logins at all. "
                    + "The previous snapshot is kept as is.");
            return SyncOutcome.skipped("empty response");
        }

        Instant now = Instant.now();
        Set<String> logins = new HashSet<>();
        int links = 0;
        List<ProviderLogin> snapshot = new ArrayList<>();
        for (ProviderLoginSource.ProviderLoginRow row : rows) {
            if (row.login() == null || row.login().isBlank()) {
                log.warn("Provider returned a multimerchant login without a name; the row is ignored");
                continue;
            }
            logins.add(row.login());
            if (row.merchantRid() != null) {
                links++;
            }
            snapshot.add(ProviderLogin.builder()
                    .login(row.login())
                    .loginStatus(row.loginStatus())
                    .linkStatus(row.linkStatus())
                    .merchantRid(row.merchantRid())
                    .merchantTitle(row.merchantTitle())
                    .syncedAt(now)
                    .build());
        }

        if (snapshot.isEmpty()) {
            log.error("Provider login sync skipped: no row carried a login. The previous snapshot is kept as is.");
            return SyncOutcome.skipped("empty response");
        }

        transactionTemplate.executeWithoutResult(status -> {
            repository.deleteAllInBatch();
            repository.saveAll(snapshot);
        });
        log.info("Provider login sync applied: {} multimerchant logins, {} merchant links", logins.size(), links);
        return new SyncOutcome(true, logins.size(), links, null);
    }
}
