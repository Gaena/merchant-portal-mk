package az.millikart.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

// scanBasePackages = "az.millikart" находит компоненты common, но НЕ переносит пакеты, где Spring
// Boot ищет JPA-сущности и репозитории: те по умолчанию — пакет этого класса и ниже. Журнал аудита
// живёт в az.millikart.common.audit (P2-14), поэтому оба скана заданы явно и оба обязаны называть
// ещё и собственный пакет сервиса: явный список заменяет умолчание, иначе свои сущности пропадут.
@SpringBootApplication(scanBasePackages = "az.millikart")
@EntityScan(basePackages = {"az.millikart.auth", "az.millikart.common.audit"})
@EnableJpaRepositories(basePackages = {"az.millikart.auth", "az.millikart.common.audit"})
@EnableScheduling // RefreshTokenCleanupScheduler (P1-12); другого расписания в auth нет.
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
