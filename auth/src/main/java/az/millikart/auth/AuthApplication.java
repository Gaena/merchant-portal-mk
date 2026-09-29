package az.millikart.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

// scanBasePackages не расширяет поиск сущностей и репозиториев: оба скана явные и называют и свой
// пакет, и журнал аудита — явный список заменяет умолчание (AGENTS.md §10).
@SpringBootApplication(scanBasePackages = "az.millikart")
@EntityScan(basePackages = {"az.millikart.auth", "az.millikart.common.audit"})
@EnableJpaRepositories(basePackages = {"az.millikart.auth", "az.millikart.common.audit"})
@EnableScheduling // RefreshTokenCleanupScheduler (P1-12) и InactiveAccountScheduler (Р-101).
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
