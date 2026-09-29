package az.millikart.pbl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;


// scanBasePackages не расширяет поиск сущностей и репозиториев (AGENTS.md §10): оба скана явные и
// называют и свой пакет, и az.millikart.common.audit (P2-14), иначе — Not a managed type.
@SpringBootApplication(scanBasePackages = "az.millikart")
@EntityScan(basePackages = {"az.millikart.pbl", "az.millikart.common.audit"})
@EnableJpaRepositories(basePackages = {"az.millikart.pbl", "az.millikart.common.audit"})
@EnableScheduling
public class PblApplication {


    public static void main(String[] args) {
        SpringApplication.run(PblApplication.class, args);
    }

}
