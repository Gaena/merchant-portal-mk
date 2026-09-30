package az.millikart.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// Таблица отказов AGENTS §5 через настоящую маршрутизацию @ExceptionHandler: контроллер бросает, MockMvc
// отдаёт ответ. Вызов метода обработчика напрямую не заметил бы, что исключение ушло не в тот обработчик —
// например, неизвестный исход денежной операции в общий 500.
class GlobalExceptionHandlerTest {

    @RestController
    static class ThrowingController {

        RuntimeException next;

        @GetMapping("/boom")
        void boom() {
            throw next;
        }

        @PostMapping("/echo")
        Payload echo(@RequestBody Payload payload) {
            return payload;
        }
    }

    record Payload(String password, Integer attempts) {
    }

    private final ThrowingController controller = new ThrowingController();
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private final Logger handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();

    @BeforeEach
    void captureLog() {
        events.start();
        handlerLogger.addAppender(events);
    }

    @AfterEach
    void releaseLog() {
        handlerLogger.detachAppender(events);
    }

    static Stream<Arguments> refusals() {
        return Stream.of(
                Arguments.of(new BusinessException("refused"), 400),
                Arguments.of(new UnauthorizedException("who are you"), 401),
                Arguments.of(new InvalidStateException("Access denied"), 403),
                Arguments.of(new ResourceNotFoundException("no such link"), 404),
                Arguments.of(new ConflictException("already exists"), 409),
                // Параллельная правка и занятая строка (NOWAIT, Р-85): повтор безопасен — 409, а не 500.
                Arguments.of(new OptimisticLockingFailureException("stale"), 409),
                Arguments.of(new PessimisticLockingFailureException("locked"), 409),
                // Не наследник BusinessException: 400 читался бы как «отказ, повторяй», а это двойной возврат.
                Arguments.of(new PaymentOutcomeUnknownException("Read timed out"), 502),
                Arguments.of(new IllegalStateException("bug"), 500));
    }

    // ERROR — только то, что требует человека (Р-98): ожидаемый отказ клиенту в ERROR не пишется.
    @ParameterizedTest
    @MethodSource("refusals")
    void eachExceptionGetsItsStatus_andOnlyServerSideFailuresAreErrors(RuntimeException exception, int expected) throws Exception {
        controller.next = exception;

        mockMvc.perform(get("/boom"))
                .andExpect(status().is(expected))
                .andExpect(jsonPath("$.status").value(expected))
                .andExpect(jsonPath("$.path").value("/boom"));

        assertEquals(expected >= 500, !atLevel(Level.ERROR).isEmpty(),
                exception.getClass().getSimpleName() + " logged " + atLevel(Level.ERROR));
    }

    // Маркер ищет мониторинг (deployment_guide, grep по PAYMENT_OUTCOME_UNKNOWN): без него неподтверждённый
    // возврат не увидит никто, кроме мерчанта, получившего 502.
    @Test
    void anUnknownPaymentOutcome_isLoggedWithTheMonitoringMarker() throws Exception {
        controller.next = new PaymentOutcomeUnknownException("Read timed out");

        mockMvc.perform(get("/boom"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(
                        "No confirmation received from the acquirer. Check the transaction status before retrying."));

        List<ILoggingEvent> errors = atLevel(Level.ERROR);
        assertEquals(1, errors.size(), String.valueOf(errors));
        assertTrue(errors.getFirst().getFormattedMessage().startsWith("PAYMENT_OUTCOME_UNKNOWN: GET /boom"),
                errors.getFirst().getFormattedMessage());
    }

    // Битое тело — ошибка клиента, а не 500 со стектрейсом.
    @Test
    void aMalformedBody_isABadRequest() throws Exception {
        mockMvc.perform(post("/echo").contentType(MediaType.APPLICATION_JSON).content("{\"password\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request payload format or parameter value"));

        assertTrue(atLevel(Level.ERROR).isEmpty());
    }

    // Сообщение Jackson цитирует нераспознанный токен и значение не того типа: пароль без кавычек ушёл бы
    // в лог (P0-9). В логе — вид ошибки, место и имя поля, тела нет.
    @ParameterizedTest
    @ValueSource(strings = {"{\"password\": S3cretToken99}", "{\"attempts\": \"S3cretToken99\"}"})
    void aMalformedBody_neverReachesTheLog(String body) throws Exception {
        mockMvc.perform(post("/echo").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        List<ILoggingEvent> warnings = atLevel(Level.WARN);
        assertEquals(1, warnings.size(), String.valueOf(warnings));
        String logged = warnings.getFirst().getFormattedMessage();
        assertFalse(logged.contains("S3cretToken99"), logged);
        assertTrue(logged.startsWith("Unreadable request body in POST /echo: "), logged);
        assertTrue(logged.contains(" at line 1, column "), logged);
    }

    // Имя поля помогает разбору, а в лог оно идёт как есть — только простое.
    @Test
    void aValueOfTheWrongType_isLoggedWithItsFieldName() throws Exception {
        mockMvc.perform(post("/echo").contentType(MediaType.APPLICATION_JSON).content("{\"attempts\": \"many\"}"))
                .andExpect(status().isBadRequest());

        assertTrue(atLevel(Level.WARN).getFirst().getFormattedMessage().endsWith(", field attempts"),
                atLevel(Level.WARN).getFirst().getFormattedMessage());
    }

    private List<ILoggingEvent> atLevel(Level level) {
        return events.list.stream().filter(event -> event.getLevel() == level).toList();
    }
}
