package az.millikart.ecom.config;

import java.time.Duration;
import java.time.ZoneId;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

// Настройки чтения из схемы шлюза. Схема — в конфигурации, а не в SQL: на стенде и в проде
// владелец объектов может отличаться, и подмена правкой запросов однажды прочитает не ту базу.
@Component
@ConfigurationProperties("ecom.txpg")
@Getter
@Setter
public class TxpgProperties {

    private String schema = "TXPG";

    private Duration queryTimeout = Duration.ofSeconds(30);

    private int fetchSize = 200;

    // Потолок периода одной выборки — наш, не провайдера: выписку за три года по операционной
    // базе обслуживал бы тот же инстанс, что проводит авторизации.
    private Duration maxWindow = Duration.ofDays(92);

    private int maxPageSize = 200;

    // Даты в схеме шлюза — местное время без пояса, Asia/Baku (подтверждено провайдером 14.09.2026).
    // Ошибка здесь сдвигает границы периода и время каждой операции на разницу поясов.
    private ZoneId zone = ZoneId.of("Asia/Baku");

    // Три опроса по пятнадцать минут: сбой на их стороне должен продержаться три четверти часа,
    // чтобы дойти до наших терминалов (Р-66).
    private int missingRunsBeforeDisable = 3;
}
