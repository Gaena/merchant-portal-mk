package az.millikart.directory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import az.millikart.directory.repository.PaymentLinkStatusRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

// P2-8: блокировка терминала обязана работать на базе, где pbl ни разу не мигрировал — первый
// деплой, в котором directory поднялся раньше. Ссылок там нет вовсе, поэтому «не перенесли ни
// одной» и есть верный ответ; раньше нативный update бил в отсутствующую таблицу и админ получал
// 500. Тест намеренно не спринговый: контекст модуля делит одну H2 с остальными тестами JVM.
@DisplayName("blocking a terminal survives a database without pbl's schema (P2-8)")
class PaymentLinkStatusRepositoryTest {

    private Connection keepAlive;
    private PaymentLinkStatusRepository repository;

    @BeforeEach
    void openEmptyDatabase(TestInfo testInfo) throws Exception {
        String url = "jdbc:h2:mem:no-pbl-" + testInfo.getTestMethod().orElseThrow().getName()
                + ";MODE=PostgreSQL";
        keepAlive = DriverManager.getConnection(url, "sa", "");

        DataSource dataSource = new DriverManagerDataSource(url, "sa", "");
        repository = new PaymentLinkStatusRepository(dataSource);
    }

    @AfterEach
    void closeDatabase() throws Exception {
        keepAlive.close();
    }

    // Все три метода возвращают ноль и ни один не трогает EntityManager, которого здесь вообще нет:
    // дойти до запроса при отсутствующей таблице означало бы громко упасть.
    @Test
    void withoutPaymentLinksTable_everyUpdateReportsNothingMoved() {
        Instant now = Instant.now();

        assertThatCode(() -> {
            assertThat(repository.suspendActiveLinks(5)).isZero();
            assertThat(repository.resumeSuspendedLinks(5, now)).isZero();
            assertThat(repository.expireSuspendedLinks(5, now)).isZero();
        }).doesNotThrowAnyException();
    }
}
