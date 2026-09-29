package az.millikart.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Не вешать @Cacheable на метод с проверкой доступа внутри: при попадании в кэш тело, а с ним и
// проверка, не выполняется (P0-3). Кэшировать можно лишь не зависящее от актора.
@Configuration
@EnableCaching
public class CacheConfig {

    // Ни одного @Cacheable нет (P0-3, Р-9); бин оставлен намеренно — кандидат pbl, где validateAccess
    // ходит в findById на каждой операции.
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
