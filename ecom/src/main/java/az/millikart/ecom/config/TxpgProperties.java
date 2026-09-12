package az.millikart.ecom.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Настройки чтения из схемы шлюза.
 *
 * `schema` вынесена в конфигурацию, а не зашита в SQL: на стенде и в проде владелец объектов
 * может отличаться, и подменять его правкой запросов — верный способ однажды прочитать не ту базу.
 *
 * `maxWindow` ограничивает период одной выборки. Ограничение наше, а не провайдера: без него
 * мерчант запросит выписку за три года по операционной базе шлюза, и отвечать на это будет тот же
 * инстанс, который в этот момент проводит авторизации.
 */
@Component
@ConfigurationProperties("ecom.txpg")
@Getter
@Setter
public class TxpgProperties {

    private String schema = "TXPG";

    private Duration queryTimeout = Duration.ofSeconds(30);

    private int fetchSize = 200;

    private Duration maxWindow = Duration.ofDays(92);

    private int maxPageSize = 200;

    /**
     * Сколько опросов подряд терминал должен не приходить, прежде чем считаться выключенным.
     * При периоде в пятнадцать минут три опроса — это три четверти часа: столько должен
     * продержаться сбой на их стороне, чтобы он дошёл до наших терминалов.
     */
    private int missingRunsBeforeDisable = 3;
}
