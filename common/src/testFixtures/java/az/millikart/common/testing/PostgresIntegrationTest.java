package az.millikart.common.testing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

// Spring-тест на настоящей PostgreSQL. Одна аннотация на все такие классы: контексты кэшируются по
// конфигурации, и разнобой (без MockMvc, другой порядок импортов) поднимал бы на каждый класс свой контекст
// с миграциями. Свои @Import, @MockBean и @SpyBean класс добавляет сверху — только если без них никак.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
public @interface PostgresIntegrationTest {
}
