package az.millikart.ecom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Сервис эквайринговых платежей: вкладка E-commerce для мерчантов, чьи платежи мы не порождаем.
 *
 * Источник истины — операционная схема платёжного шлюза (TXPG), и ходим мы в неё **только
 * читать**. Своя PostgreSQL здесь одна на весь портал и держит ровно две вещи: привязки наших
 * компаний к мерчантам провайдера и общий журнал аудита.
 *
 * Почему отдельный сервис, а не ещё один контроллер в pbl: там наши собственные платежи, наша
 * же база и наша ответственность за их исход. Здесь чужая операционная база, чужой контракт и
 * чужой SLA — смешивать их в одном процессе значит связать судьбу платёжных ссылок с доступностью
 * чужого Oracle.
 *
 * Как и в pbl, оба скана объявлены явно: `scanBasePackages` находит компоненты common, но пакеты
 * для сущностей и репозиториев берутся от пакета этого класса, и журнал аудита из
 * `az.millikart.common.audit` без явной записи не нашёлся бы.
 */
@SpringBootApplication(scanBasePackages = "az.millikart")
@EntityScan(basePackages = {"az.millikart.ecom", "az.millikart.common.audit"})
@EnableJpaRepositories(basePackages = {"az.millikart.ecom", "az.millikart.common.audit"})
@EnableScheduling
public class EcomApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcomApplication.class, args);
    }
}
