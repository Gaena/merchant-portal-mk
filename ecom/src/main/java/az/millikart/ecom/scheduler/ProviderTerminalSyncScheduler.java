package az.millikart.ecom.scheduler;

import az.millikart.ecom.service.ProviderTerminalSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Периодическое обновление слепка терминалов провайдера.
 *
 * Расписанием, а не входом администратора. Вход — событие случайное: администратор не заходил
 * неделю, и неделю ничего не обновлялось; зато аутентификация начинала зависеть от доступности
 * чужой базы, и недоступный шлюз означал бы, что в портал никто не войдёт. Обновить слепок
 * прямо сейчас администратор может кнопкой — осознанно и не платя за это входом.
 */
@Component
@ConditionalOnProperty(name = "ecom.terminal-sync.enabled", havingValue = "true", matchIfMissing = true)
public class ProviderTerminalSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProviderTerminalSyncScheduler.class);

    private final ProviderTerminalSyncService service;

    public ProviderTerminalSyncScheduler(ProviderTerminalSyncService service) {
        this.service = service;
    }

    @Scheduled(cron = "${ecom.terminal-sync.cron:0 */15 * * * *}")
    public void run() {
        try {
            service.sync();
        } catch (RuntimeException e) {
            // Планировщик молчащий по умолчанию: необработанное исключение остановило бы
            // расписание целиком, и следующий проход не случился бы никогда.
            log.error("Provider terminal sync failed: {}", e.getMessage(), e);
        }
    }
}
