package az.millikart.ecom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

// Отдельный сервис: в одном процессе с платёжными ссылками их судьба зависела бы от чужого Oracle
// (Р-65). Сканы JPA заданы явно, иначе журнал аудита из common не найдётся (AGENTS.md §10).
@SpringBootApplication(scanBasePackages = "az.millikart")
@EntityScan(basePackages = {"az.millikart.ecom", "az.millikart.common.audit"})
@EnableJpaRepositories(basePackages = {"az.millikart.ecom", "az.millikart.common.audit"})
@EnableScheduling
public class EcomApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcomApplication.class, args);
    }
}
