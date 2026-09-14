package az.millikart.directory;

import az.millikart.common.testing.PostgresTestContainer;
import az.millikart.common.audit.AuditLog;
import az.millikart.common.audit.AuditOutcome;
import az.millikart.common.security.JwtProvider;
import az.millikart.common.security.UserPrincipal;
import az.millikart.directory.domain.TerminalStatus;
import az.millikart.directory.dto.CreateCompanyRequest;
import az.millikart.directory.dto.CreateTerminalRequest;
import az.millikart.directory.dto.UpdateTerminalRequest;
import az.millikart.directory.repository.CompanyRepository;
import az.millikart.directory.repository.PaymentLinkStatusRepository;
import az.millikart.directory.repository.TerminalRepository;
import az.millikart.directory.service.TerminalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// P2-8 (Р-37, Р-39, Р-40): блокировка терминала вместо удаления и что она делает с его платёжными
// ссылками. Таблица payment_links принадлежит pbl, и в H2 этого модуля не заводится, поэтому её
// создаёт настоящий changelog pbl через SharedDatabaseSchema: так же выглядит прод (одна общая
// база), и только так нативные update-ы проверяются против реальной таблицы.
//
// Пишет в `payment_links` — таблицу чужого модуля — нативным запросом, и проверяет, что
// блокировка терминала двигает ссылки. Диалект здесь существенный, а не безразличный.
@SpringBootTest
@Import(PostgresTestContainer.class)
@AutoConfigureMockMvc
public class TerminalBlockingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private TerminalRepository terminalRepository;

    @Autowired
    private AuditLogTestRepository auditLogRepository;

    @Autowired
    private TerminalService terminalService;

    @SpyBean
    private PaymentLinkStatusRepository paymentLinkStatusRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String employeeTokenCompany1;

    // Номера выдаёт база при заведении (Р-81), поэтому поля, а не константы.
    private int terminal;
    private int otherTerminal;

    @BeforeEach
    public void setup() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            SharedDatabaseSchema.applyPblChangelog(connection);
        }
        reset(paymentLinkStatusRepository);

        jdbcTemplate.update("DELETE FROM payment_links");
        auditLogRepository.deleteAll();
        terminalRepository.deleteAll();
        companyRepository.deleteAll();

        adminToken = "Bearer " + jwtProvider.generateToken("000", "admin@millikart.az", "SYSTEM_ADMIN", null);
        employeeTokenCompany1 = "Bearer " + jwtProvider.generateToken("444", "employee@comp1.com", "COMPANY_EMPLOYEE", "comp-01");

        createCompany("comp-01", "MilliKart LLC");
        terminal = createTerminal("Main Terminal");
        otherTerminal = createTerminal("Second Terminal");
    }

    // 1-3. Блокировка приостанавливает нужные ссылки и только их

    @Test
    public void blockingTerminal_suspendsItsActiveLinks() throws Exception {
        UUID active = seedLink(terminal, "ACTIVE", Instant.now().plus(1, ChronoUnit.DAYS));
        UUID alsoActive = seedLink(terminal, "ACTIVE", null);

        block(terminal);

        assertThat(statusOf(active)).isEqualTo("SUSPENDED");
        assertThat(statusOf(alsoActive)).isEqualTo("SUSPENDED");
        assertThat(terminalRepository.findById(terminal).orElseThrow().getStatus())
                .isEqualTo(TerminalStatus.BLOCKED);
    }

    // EXPIRED, COMPLETED и CANCELED говорят, чем ссылка кончилась. Блокировка не имеет права это
    // затирать: разблокировке пришлось бы гадать, чем каждая из них была.
    @Test
    public void blockingTerminal_leavesFinishedLinksAlone() throws Exception {
        UUID expired = seedLink(terminal, "EXPIRED", Instant.now().minus(1, ChronoUnit.DAYS));
        UUID completed = seedLink(terminal, "COMPLETED", Instant.now().plus(1, ChronoUnit.DAYS));
        UUID canceled = seedLink(terminal, "CANCELED", Instant.now().plus(1, ChronoUnit.DAYS));

        block(terminal);

        assertThat(statusOf(expired)).isEqualTo("EXPIRED");
        assertThat(statusOf(completed)).isEqualTo("COMPLETED");
        assertThat(statusOf(canceled)).isEqualTo("CANCELED");
    }

    @Test
    public void blockingTerminal_leavesAnotherTerminalsLinksAlone() throws Exception {
        UUID mine = seedLink(terminal, "ACTIVE", Instant.now().plus(1, ChronoUnit.DAYS));
        UUID neighbours = seedLink(otherTerminal, "ACTIVE", Instant.now().plus(1, ChronoUnit.DAYS));

        block(terminal);

        assertThat(statusOf(mine)).isEqualTo("SUSPENDED");
        assertThat(statusOf(neighbours)).isEqualTo("ACTIVE");
    }

    // 4-5. Разблокировка разбирает приостановленные ссылки по их сроку

    @Test
    public void unblockingTerminal_reactivatesSuspendedLinks() throws Exception {
        UUID link = seedLink(terminal, "ACTIVE", Instant.now().plus(1, ChronoUnit.DAYS));
        UUID noDeadline = seedLink(terminal, "ACTIVE", null);
        block(terminal);

        unblock(terminal);

        assertThat(statusOf(link)).isEqualTo("ACTIVE");
        assertThat(statusOf(noDeadline))
                .as("a link without a deadline is payable, so it comes back too")
                .isEqualTo("ACTIVE");
    }

    // Смысл Р-40: ссылка, срок которой истёк во время блокировки, возвращается как EXPIRED. Вернуть
    // её в ACTIVE значило бы рекламировать ссылку, отказывающую каждому плательщику до прихода
    // sweep — а sweep смотрит только на ACTIVE, так что исправить это было бы уже некому.
    @Test
    public void unblockingTerminal_expiresLinksWhoseDeadlinePassedDuringTheBlock() throws Exception {
        UUID stillGood = seedLink(terminal, "ACTIVE", Instant.now().plus(1, ChronoUnit.DAYS));
        UUID ranOut = seedLink(terminal, "ACTIVE", Instant.now().plus(1, ChronoUnit.DAYS));
        block(terminal);
        // Срок истекает, пока терминал заблокирован.
        jdbcTemplate.update("UPDATE payment_links SET expires_at = ? WHERE id = ?",
                utc(Instant.now().minus(1, ChronoUnit.HOURS)), ranOut);

        unblock(terminal);

        assertThat(statusOf(stillGood)).isEqualTo("ACTIVE");
        assertThat(statusOf(ranOut)).isEqualTo("EXPIRED");
    }

    // 6. Установка уже имеющегося статуса не трогает ничего

    @Test
    public void settingTheSameStatus_doesNotTouchLinks() throws Exception {
        UUID link = seedLink(terminal, "ACTIVE", Instant.now().plus(1, ChronoUnit.DAYS));

        // ACTIVE -> ACTIVE: PATCH, повторяющий объект как есть, вообще не должен быть событием.
        mockMvc.perform(patch("/api/v1/terminals/" + terminal)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateTerminalRequest(null, null, null, null, TerminalStatus.ACTIVE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")));

        assertThat(statusOf(link)).isEqualTo("ACTIVE");

        block(terminal);
        assertThat(statusOf(link)).isEqualTo("SUSPENDED");

        // BLOCKED -> BLOCKED: то же самое, и в особенности нельзя заново прогонять приостановку по
        // ссылкам, которые с тех пор изменил кто-то другой.
        jdbcTemplate.update("UPDATE payment_links SET status = 'CANCELED' WHERE id = ?", link);
        mockMvc.perform(patch("/api/v1/terminals/" + terminal)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateTerminalRequest(null, null, null, null, TerminalStatus.BLOCKED))))
                .andExpect(status().isOk());

        assertThat(statusOf(link)).isEqualTo("CANCELED");
    }

    // 7. Одна транзакция: сбой обновления ссылок обязан откатить и саму блокировку — терминал не
    // может остаться заблокированным, пока его ссылки платятся.
    @Test
    public void failureToSuspendLinks_rollsBackTheBlockItself() throws Exception {
        UUID link = seedLink(terminal, "ACTIVE", Instant.now().plus(1, ChronoUnit.DAYS));
        doThrow(new DataAccessResourceFailureException("links are unreachable"))
                .when(paymentLinkStatusRepository).suspendActiveLinks(anyInt());

        assertThatThrownBy(() -> terminalService.updateTerminal(terminal,
                new UpdateTerminalRequest(null, null, null, null, TerminalStatus.BLOCKED),
                adminPrincipal()))
                .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(terminalRepository.findById(terminal).orElseThrow().getStatus())
                .as("a terminal must never be blocked while its links stay payable")
                .isEqualTo(TerminalStatus.ACTIVE);
        assertThat(statusOf(link)).isEqualTo("ACTIVE");
    }

    // 9. Массовое изменение оставляет след, и с числами

    @Test
    public void blockingAndUnblocking_areRecordedWithTheLinkCounts() throws Exception {
        seedLink(terminal, "ACTIVE", Instant.now().plus(1, ChronoUnit.DAYS));
        seedLink(terminal, "ACTIVE", Instant.now().plus(1, ChronoUnit.DAYS));
        auditLogRepository.deleteAll();

        block(terminal);

        AuditLog blocked = auditRecord("BLOCK");
        assertThat(blocked.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(blocked.getEntityId()).isEqualTo(String.valueOf(terminal));
        assertThat(blocked.getDetails())
                .as("the number of links is the only trace the bulk change happened")
                .contains("suspended 2 links");

        unblock(terminal);

        assertThat(auditRecord("UNBLOCK").getDetails()).contains("resumed 2 links", "expired 0 links");
    }

    // 10. Блокировка — запись, и требует прав на запись

    @Test
    public void blockingTerminal_asEmployee_isRefusedAndChangesNothing() throws Exception {
        UUID link = seedLink(terminal, "ACTIVE", Instant.now().plus(1, ChronoUnit.DAYS));

        mockMvc.perform(patch("/api/v1/terminals/" + terminal)
                        .header(HttpHeaders.AUTHORIZATION, employeeTokenCompany1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateTerminalRequest(null, null, null, null, TerminalStatus.BLOCKED))))
                .andExpect(status().isForbidden());

        assertThat(terminalRepository.findById(terminal).orElseThrow().getStatus())
                .isEqualTo(TerminalStatus.ACTIVE);
        assertThat(statusOf(link)).isEqualTo("ACTIVE");
    }

    // Фикстуры

    private UserPrincipal adminPrincipal() {
        return new UserPrincipal("000", "admin@millikart.az", "SYSTEM_ADMIN", null);
    }

    private void block(int terminalId) throws Exception {
        setStatus(terminalId, TerminalStatus.BLOCKED);
    }

    private void unblock(int terminalId) throws Exception {
        setStatus(terminalId, TerminalStatus.ACTIVE);
    }

    private void setStatus(int terminalId, TerminalStatus status) throws Exception {
        mockMvc.perform(patch("/api/v1/terminals/" + terminalId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateTerminalRequest(null, null, null, null, status))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(status.name())));
    }

    private AuditLog auditRecord(String action) {
        List<AuditLog> records = auditLogRepository.findAll().stream()
                .filter(record -> action.equals(record.getAction()))
                .toList();
        assertThat(records).as("expected exactly one %s record", action).hasSize(1);
        return records.getFirst();
    }

    // Вставка через SQL: PaymentLink намеренно не является сущностью этого модуля.
    private UUID seedLink(int terminalId, String status, Instant expiresAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO payment_links
                            (id, version, provider_reference, merchant_order_id, terminal_id, amount,
                             currency, payment_type, usage_type, current_payments_count, status,
                             expires_at, created_at)
                        VALUES (?, 0, ?, ?, ?, 10.00, 'AZN', 'SMS', 'SINGLE', 0, ?, ?, ?)""",
                id, "RID-" + id.toString().substring(0, 8), "order-" + id.toString().substring(0, 8),
                terminalId, status,
                expiresAt != null ? utc(expiresAt) : null,
                utc(Instant.now()));
        return id;
    }

    private String statusOf(UUID linkId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM payment_links WHERE id = ?", String.class, linkId);
    }

    private void createCompany(String id, String name) throws Exception {
        mockMvc.perform(post("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCompanyRequest(id, name))))
                .andExpect(status().isCreated());
    }

    // --- пароль терминала ------------------------------------------------------------------

    // Пароль эквайринга уходит наружу ровно одним путём и только администратору системы.
    // Каждое чтение оставляет след: посмотреть чужой платёжный ключ — как раз то событие,
    // ради которого журнал и заведён.
    @Test
    public void password_isRevealedToASystemAdmin_andRecorded() throws Exception {
        mockMvc.perform(get("/api/v1/terminals/{id}/password", terminal)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(terminal)))
                .andExpect(jsonPath("$.password", is("term_pass")));

        List<AuditLog> reads = auditLogRepository.findAll().stream()
                .filter(record -> "READ".equals(record.getAction())
                        && String.valueOf(terminal).equals(record.getEntityId()))
                .toList();
        assertThat(reads).hasSize(1);
        assertThat(reads.getFirst().getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(reads.getFirst().getPerformedBy()).isEqualTo("admin@millikart.az");
        // Сам ключ в журнал не попадает: журнал читают не только те, кому пароль полагается.
        assertThat(reads.getFirst().getDetails()).doesNotContain("term_pass");
    }

    // Сотруднику компании пароль не показывают, хотя терминалы своей компании он читает свободно.
    @Test
    public void password_isRefusedToEveryoneElse_andTheAttemptIsRecorded() throws Exception {
        mockMvc.perform(get("/api/v1/terminals/{id}/password", terminal)
                        .header(HttpHeaders.AUTHORIZATION, employeeTokenCompany1))
                .andExpect(status().isForbidden());

        List<AuditLog> denied = auditLogRepository.findAll().stream()
                .filter(record -> record.getOutcome() == AuditOutcome.DENIED)
                .toList();
        assertThat(denied).hasSize(1);
        assertThat(denied.getFirst().getAction()).isEqualTo("READ");
        assertThat(denied.getFirst().getPerformedBy()).isEqualTo("employee@comp1.com");
    }

    // В обычном ответе по терминалу пароль как был замаскирован, так и остаётся: отдельный путь
    // заведён именно для того, чтобы списки и карточки ключа не несли.
    @Test
    public void terminalResponse_keepsThePasswordMasked() throws Exception {
        mockMvc.perform(get("/api/v1/terminals/{id}", terminal)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password", is("********")));
    }

    // Менять пароль тоже может только администратор системы: молча проигнорировать чужую попытку
    // нельзя — глава компании решил бы, что ключ сменён, и остался бы со старым.
    @Test
    public void passwordChange_byAnyoneButASystemAdmin_isRefusedAndChangesNothing() throws Exception {
        String headTokenCompany1 = "Bearer " + jwtProvider.generateToken(
                "111", "head@comp1.com", "COMPANY_HEAD", "comp-01");

        mockMvc.perform(patch("/api/v1/terminals/{id}", terminal)
                        .header(HttpHeaders.AUTHORIZATION, headTokenCompany1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"stolen-key\"}"))
                .andExpect(status().isForbidden());

        assertThat(terminalRepository.findById(terminal).orElseThrow().getPassword())
                .isEqualTo("term_pass");
    }

    // Остальные поля глава компании правит по-прежнему: ограничение касается ключа, а не терминала.
    @Test
    public void nameChange_byACompanyHead_stillGoesThrough() throws Exception {
        String headTokenCompany1 = "Bearer " + jwtProvider.generateToken(
                "111", "head@comp1.com", "COMPANY_HEAD", "comp-01");

        mockMvc.perform(patch("/api/v1/terminals/{id}", terminal)
                        .header(HttpHeaders.AUTHORIZATION, headTokenCompany1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed Terminal\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Renamed Terminal")));
    }

    /**
     * Момент времени так, как его пишет приложение.
     *
     * Колонка `expires_at` объявлена как `timestamp` без зоны, и Hibernate кладёт в неё `Instant`
     * в UTC. `java.sql.Timestamp.from(...)`, который стоял здесь раньше, драйвер переводит в
     * **локальную зону JVM**: на машине в Баку срок «час назад» ложился в базу как «через три
     * часа», разблокировка считала ссылку живой и возвращала её в ACTIVE вместо EXPIRED.
     * На H2 расхождение не проявлялось — поймалось сразу после переезда на PostgreSQL.
     */
    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private int createTerminal(String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/terminals")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateTerminalRequest(name, "term_login", "term_pass", "comp-01", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asInt();
    }
}
