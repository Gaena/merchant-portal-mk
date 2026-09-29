package az.millikart.directory;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// scanBasePackages не расширяет сканы JPA, поэтому они заданы явно (P2-14, AGENTS.md §10). Свой
// пакет в них обязателен: явный список заменяет умолчание.
@SpringBootApplication(scanBasePackages = "az.millikart")
@EnableScheduling
@EntityScan(basePackages = {"az.millikart.directory", "az.millikart.common.audit"})
@EnableJpaRepositories(basePackages = {"az.millikart.directory", "az.millikart.common.audit"})
public class DirectoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(DirectoryApplication.class, args);
    }
}
