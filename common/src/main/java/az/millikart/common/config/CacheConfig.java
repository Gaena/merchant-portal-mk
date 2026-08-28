package az.millikart.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Не вешать @Cacheable на метод, внутри которого выполняется проверка доступа: ровно на этом была
// построена дыра P0-3. Ключом был #id, а validateAccess стоял в теле метода — при попадании в кэш
// тело не выполнялось, вместе с ним и проверка, и сотрудник чужой компании получал данные терминала
// на все 15 минут TTL. Кэшировать можно лишь независящее от актора, проверку оставляя некэшируемой.
@Configuration
@EnableCaching
public class CacheConfig {

    // Caffeine, TTL 15 минут, но сейчас ни один кэш не используется: terminals и companies
    // обслуживали TerminalService.getTerminal и CompanyService.getCompany в directory, 17.08.2026
    // аннотации сняты (P0-3, решение Р-9). Конфигурация оставлена намеренно — кандидат это pbl, где
    // validateAccess ходит в findById на каждой операции; кэш directory жил в другом сервисе.
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("terminals", "companies");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(500)
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .recordStats());
        return cacheManager;
    }
}
