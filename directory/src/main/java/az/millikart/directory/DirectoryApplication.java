package az.millikart.directory;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// scanBasePackages = "az.millikart" находит компоненты common, но не переносит пакеты сканирования
// JPA-сущностей и репозиториев: те по умолчанию берутся от пакета этого класса. Журнал живёт
// в az.millikart.common.audit (P2-14), потому оба скана заданы явно — и оба обязаны называть
// собственный пакет сервиса тоже: явный список заменяет умолчание, иначе свои сущности пропадут.
@SpringBootApplication(scanBasePackages = "az.millikart")
// Расписание нужно ровно одной задаче — сверке статусов терминалов с провайдером
// (TerminalStatusReconciliationScheduler).
@EnableScheduling
@EntityScan(basePackages = {"az.millikart.directory", "az.millikart.common.audit"})
@EnableJpaRepositories(basePackages = {"az.millikart.directory", "az.millikart.common.audit"})
public class DirectoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(DirectoryApplication.class, args);
    }
}
