package az.millikart.ecom.config;

import java.time.Duration;
import java.time.ZoneId;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

// Схема — в конфигурации, а не в SQL: владелец объектов на стенде и в проде может отличаться.
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

    // Потолок заказов, просматриваемых за запрос с фильтром по статусу (Р-87): без него редкий статус
    // за 92 дня означал бы скан всего периода за одно нажатие.
    private int statusScanLimit = 1000;

    // Даты шлюза — местное время без пояса (подтверждено провайдером). Ошибка здесь сдвигает границы
    // периода и время каждой операции на разницу поясов.
    private ZoneId zone = ZoneId.of("Asia/Baku");

    // Три опроса по пятнадцать минут: сбой на их стороне должен продержаться три четверти часа,
    // чтобы дойти до наших терминалов (Р-66).
    private int missingRunsBeforeDisable = 3;
}
