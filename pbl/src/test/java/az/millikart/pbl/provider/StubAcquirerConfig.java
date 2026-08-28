package az.millikart.pbl.provider;

import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// Ставит StubAcquiringClient перед настоящим эквайером в одном тестовом контексте; импортируется
// явно теми классами, что реально прогоняют платёж. Раньше подмену включал pbl.provider.stub —
// продовая настройка, из-за которой работающий сервис можно было направить на клиента, отвечающего
// «оплачено» без эквайера. Теперь подмена не выходит за тестовые исходники.
@TestConfiguration
public class StubAcquirerConfig {

    // Мок Mockito, делегирующий дублёру: тест переопределяет отдельный ответ через doReturn, всё
    // остальное ведёт себя как дублёр, и можно verify, о чём сервис просил эквайера. Primary —
    // потому что настоящий TxpgAcquiringClient остаётся в контексте. Сам Mockito состояние между
    // методами не сбрасывает: класс, считающий вызовы, обязан звать Mockito.reset.
    @Bean
    @Primary
    public AcquiringClient stubbedAcquiringClient() {
        return Mockito.mock(AcquiringClient.class, AdditionalAnswers.delegatesTo(new StubAcquiringClient()));
    }
}
