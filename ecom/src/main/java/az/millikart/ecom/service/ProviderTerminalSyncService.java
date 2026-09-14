package az.millikart.ecom.service;

import az.millikart.ecom.config.TxpgProperties;
import az.millikart.ecom.domain.ProviderTerminal;
import az.millikart.ecom.repository.ProviderTerminalRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Обновление слепка терминалов провайдера (Р-66). Все правила — про то, чтобы чужой сбой не выключил
// наши терминалы: неудачный и пустой опрос не применяются, гасится терминал только после
// missingRunsBeforeDisable пропаданий подряд. Статусы наших терминалов сверяет directory.
@Service
public class ProviderTerminalSyncService {

    private static final Logger log = LoggerFactory.getLogger(ProviderTerminalSyncService.class);

    private final ProviderTerminalSource source;
    private final ProviderTerminalRepository repository;
    private final TxpgProperties properties;

    public ProviderTerminalSyncService(ProviderTerminalSource source,
                                       ProviderTerminalRepository repository,
                                       TxpgProperties properties) {
        this.source = source;
        this.repository = repository;
        this.properties = properties;
    }

    // Итог одного прохода — для журнала и ответа админу на ручной запуск. ambiguous — мерчанты,
    // пришедшие несколькими разными строками: их в этом проходе не обновили, но и не гасили.
    public record SyncOutcome(boolean applied, int seen, int ambiguous, int disabled, String skippedBecause) {

        public static SyncOutcome skipped(String reason) {
            return new SyncOutcome(false, 0, 0, 0, reason);
        }
    }

    @Transactional
    public SyncOutcome sync() {
        List<ProviderTerminalSource.ProviderTerminalRow> rows;
        try {
            rows = source.fetchActive();
        } catch (RuntimeException e) {
            log.error("Provider terminal sync skipped: the gateway could not be queried ({}). "
                    + "The previous snapshot is kept as is.", e.getMessage(), e);
            return SyncOutcome.skipped("gateway unavailable");
        }

        // У работающего эквайринга не бывает нуля терминалов: пустой ответ — оборванная выборка
        // или сменившийся фильтр, и действовать по нему значит остановить приём платежей всем.
        if (rows == null || rows.isEmpty()) {
            log.error("Provider terminal sync skipped: the gateway returned no terminals at all. "
                    + "An acquiring provider without a single terminal is a broken answer, not news, "
                    + "and acting on it would suspend live payment links.");
            return SyncOutcome.skipped("empty response");
        }

        // Одна строка на мерчанта (Р-67, Р-79). Одинаковые строки — это одна (например, две привязки
        // PBY у терминала); разные логины или названия у одного мерчанта сопоставить не с чем.
        Map<String, Set<ProviderTerminalSource.ProviderTerminalRow>> byRid = new LinkedHashMap<>();
        for (ProviderTerminalSource.ProviderTerminalRow row : rows) {
            if (row.rid() == null || row.rid().isBlank()) {
                log.warn("Provider returned a terminal without a rid; the row is ignored");
                continue;
            }
            byRid.computeIfAbsent(row.rid(), rid -> new LinkedHashSet<>()).add(row);
        }

        Instant now = Instant.now();
        Map<String, ProviderTerminal> known = new HashMap<>();
        repository.findAll().forEach(terminal -> known.put(terminal.getRid(), terminal));

        int seen = 0;
        int ambiguous = 0;
        for (Map.Entry<String, Set<ProviderTerminalSource.ProviderTerminalRow>> entry : byRid.entrySet()) {
            seen++;
            ProviderTerminal terminal = known.remove(entry.getKey());
            if (entry.getValue().size() > 1) {
                ambiguous++;
                keepAliveWithoutUpdating(entry.getKey(), entry.getValue().size(), terminal, now);
                continue;
            }
            ProviderTerminalSource.ProviderTerminalRow row = entry.getValue().iterator().next();
            if (terminal == null) {
                terminal = ProviderTerminal.builder()
                        .rid(row.rid())
                        .firstSeenAt(now)
                        .build();
            }
            // Логин и название всегда берутся у провайдера: он их хозяин. Сменил логин — сменился
            // и у нас, иначе терминал однажды перестанет ходить в шлюз.
            terminal.setTitle(row.title());
            terminal.setLogin(row.login());
            terminal.setActive(true);
            terminal.setMissingRuns(0);
            terminal.setLastSeenAt(now);
            terminal.setSyncedAt(now);
            repository.save(terminal);
        }

        // Всё, что осталось в known, в этой выгрузке не пришло.
        int disabled = 0;
        int threshold = properties.getMissingRunsBeforeDisable();
        for (ProviderTerminal missing : known.values()) {
            missing.setSyncedAt(now);
            if (!missing.isActive()) {
                repository.save(missing);
                continue;
            }
            missing.setMissingRuns(missing.getMissingRuns() + 1);
            if (missing.getMissingRuns() >= threshold) {
                missing.setActive(false);
                disabled++;
                log.info("Provider terminal {} was absent from {} consecutive runs and is now marked inactive",
                        missing.getRid(), missing.getMissingRuns());
            } else {
                log.info("Provider terminal {} is absent ({} of {} runs); not disabling it yet",
                        missing.getRid(), missing.getMissingRuns(), threshold);
            }
            repository.save(missing);
        }

        log.info("Provider terminal sync applied: {} terminals seen, {} ambiguous, {} marked inactive",
                seen, ambiguous, disabled);
        return new SyncOutcome(true, seen, ambiguous, disabled, null);
    }

    // Мерчант у провайдера есть, так что гасить его нельзя; но какой из логинов наш — неизвестно,
    // поэтому ни логин, ни название, ни флаг активности не трогаются, а новый не заводится вовсе.
    private void keepAliveWithoutUpdating(String rid, int variants, ProviderTerminal terminal, Instant now) {
        log.warn("Provider returned {} different rows for merchant {}; it is not updated in this run. "
                + "One merchant is expected to have exactly one e-commerce terminal login", variants, rid);
        if (terminal == null) {
            return;
        }
        terminal.setMissingRuns(0);
        terminal.setLastSeenAt(now);
        terminal.setSyncedAt(now);
        repository.save(terminal);
    }
}
