package az.millikart.ecom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

// Отдельный сервис, а не контроллер в pbl: чужая операционная база TXPG, только чтение, чужой SLA —
// в одном процессе с платёжными ссылками их судьба зависела бы от доступности чужого Oracle (Р-65).
// Оба скана объявлены явно: без этого журнал аудита из az.millikart.common.audit не нашёлся бы.
@SpringBootApplication(scanBasePackages = "az.millikart")
@EntityScan(basePackages = {"az.millikart.ecom", "az.millikart.common.audit"})
@EnableJpaRepositories(basePackages = {"az.millikart.ecom", "az.millikart.common.audit"})
@EnableScheduling
public class EcomApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcomApplication.class, args);
    }
}
