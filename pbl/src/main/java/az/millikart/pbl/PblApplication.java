package az.millikart.pbl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;


// scanBasePackages = "az.millikart" находит компоненты common, но НЕ двигает пакеты, в которых
// Spring Boot ищет JPA-сущности и репозитории: те берутся от пакета этого класса и ниже. Журнал
// аудита лежит в az.millikart.common.audit (P2-14), поэтому оба скана объявлены явно — и оба
// обязаны называть свой пакет тоже: запись заменяет умолчание, иначе свои сущности не найдутся.
@SpringBootApplication(scanBasePackages = "az.millikart")
@EntityScan(basePackages = {"az.millikart.pbl", "az.millikart.common.audit"})
@EnableJpaRepositories(basePackages = {"az.millikart.pbl", "az.millikart.common.audit"})
@EnableScheduling
public class PblApplication {


    public static void main(String[] args) {
        SpringApplication.run(PblApplication.class, args);
    }

}
