package az.millikart.ecom.service;

import az.millikart.ecom.config.TxpgProperties;
import az.millikart.ecom.domain.ProviderTerminal;
import az.millikart.ecom.repository.ProviderTerminalRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Обновление слепка терминалов провайдера.
 *
 * Три правила, и все три — про то, чтобы чужой сбой не выключил наши терминалы.
 *
 * 1. **Неудачный опрос не применяется вовсе.** Исключение из источника означает «спросить не
 *    удалось», и слепок остаётся прежним. Считать недоступность провайдера сообщением о том,
 *    что терминалов больше нет, нельзя.
 * 2. **Пустой ответ не применяется тоже.** Список без единого терминала технически валиден, но
 *    у работающего эквайринга его не бывает: это признак оборванной выборки или сменившегося
 *    фильтра на их стороне, а ценой ошибки здесь будет остановленный приём платежей.
 * 3. **Гасим после подтверждения.** Терминал выключается не первым пропаданием, а после
 *    `missingRunsBeforeDisable` опросов подряд. При периоде в пятнадцать минут это значит, что
 *    сбой должен продержаться три четверти часа, прежде чем что-то изменится.
 *
 * Статусы **наших** терминалов этот сервис не трогает. Сверкой занимается `directory`, где смена
 * статуса уже умеет приостанавливать и восстанавливать платёжные ссылки и писать в журнал аудита.
 */
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

    /** Что сделал один проход. Возвращается наружу ради журнала и ручного запуска админом. */
    public record SyncOutcome(boolean applied, int seen, int disabled, String skippedBecause) {

        public static SyncOutcome skipped(String reason) {
            return new SyncOutcome(false, 0, 0, reason);
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

        if (rows == null || rows.isEmpty()) {
            log.error("Provider terminal sync skipped: the gateway returned no terminals at all. "
                    + "An acquiring provider without a single terminal is a broken answer, not news, "
                    + "and acting on it would suspend live payment links.");
            return SyncOutcome.skipped("empty response");
        }

        Instant now = Instant.now();
        Map<String, ProviderTerminal> known = new HashMap<>();
        repository.findAll().forEach(terminal -> known.put(terminal.getRid(), terminal));

        int seen = 0;
        for (ProviderTerminalSource.ProviderTerminalRow row : rows) {
            if (row.rid() == null || row.rid().isBlank()) {
                // Строка без идентификатора не с чем сопоставить: ни завести, ни обновить.
                log.warn("Provider returned a terminal without a rid; the row is ignored");
                continue;
            }
            seen++;
            ProviderTerminal terminal = known.remove(row.rid());
            if (terminal == null) {
                terminal = ProviderTerminal.builder()
                        .rid(row.rid())
                        .firstSeenAt(now)
                        .build();
            }
            // Логин и название всегда берутся у провайдера: он их хозяин. Сменил логин —
            // сменился и у нас, иначе терминал однажды перестанет ходить в шлюз.
            terminal.setTitle(row.title());
            terminal.setLogin(row.login());
            terminal.setActive(true);
            terminal.setMissingRuns(0);
            terminal.setLastSeenAt(now);
            terminal.setSyncedAt(now);
            repository.save(terminal);
        }

        // Всё, что осталось в `known`, в этой выгрузке не пришло.
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

        log.info("Provider terminal sync applied: {} terminals seen, {} marked inactive", seen, disabled);
        return new SyncOutcome(true, seen, disabled, null);
    }
}
