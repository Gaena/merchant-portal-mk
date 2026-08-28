package az.millikart.directory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.millikart.common.exception.InvalidStateException;
import az.millikart.common.security.JwtProvider;
import az.millikart.common.security.UserPrincipal;
import az.millikart.common.audit.AuditLog;
import az.millikart.common.audit.AuditOutcome;
import az.millikart.directory.dto.CreateCompanyRequest;
import az.millikart.directory.dto.UpdateCompanyRequest;
import az.millikart.common.audit.AuditLogRepository;
import az.millikart.directory.repository.CompanyRepository;
import az.millikart.directory.repository.TerminalRepository;
import az.millikart.common.audit.AuditLogService;
import az.millikart.common.audit.AuditLogWriter;
import az.millikart.directory.service.CompanyService;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// Р-35: успех попадает в журнал только после коммита, откат не оставляет записи, отказ пишется
// несмотря на откат, а сбой журнала не валит бизнес-операцию. Р-36: адрес клиента подчиняется
// правилам доверия ClientIp и вне запроса просто отсутствует.
@SpringBootTest
@AutoConfigureMockMvc
public class AuditLogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private TerminalRepository terminalRepository;

    @SpyBean
    private AuditLogRepository auditLogRepository;

    // Чтение и очистка: репозиторий приложения умеет только добавлять строки (Р-42).
    @Autowired
    private AuditLogTestRepository auditLogs;

    @Autowired
    private CompanyService companyService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String managerTokenCompany2;
    private String employeeTokenCompany1;
    private String headTokenWithoutCompany;

    private final ListAppender<ILoggingEvent> writerLogAppender = new ListAppender<>();
    private final ListAppender<ILoggingEvent> serviceLogAppender = new ListAppender<>();

    @BeforeEach
    public void setup() {
        auditLogs.deleteAll();
        terminalRepository.deleteAll();
        companyRepository.deleteAll();

        adminToken = "Bearer " + jwtProvider.generateToken("000", "admin@millikart.az", "SYSTEM_ADMIN", null);
        managerTokenCompany2 = "Bearer " + jwtProvider.generateToken("333", "manager@comp2.com", "COMPANY_MANAGER", "comp-02");
        employeeTokenCompany1 = "Bearer " + jwtProvider.generateToken("444", "employee@comp1.com", "COMPANY_EMPLOYEE", "comp-01");
        headTokenWithoutCompany = "Bearer " + jwtProvider.generateToken("555", "head@nowhere.com", "COMPANY_HEAD", null);

        writerLogAppender.list.clear();
        writerLogAppender.start();
        writerLogger().addAppender(writerLogAppender);

        serviceLogAppender.list.clear();
        serviceLogAppender.start();
        serviceLogger().addAppender(serviceLogAppender);
    }

    @AfterEach
    public void detachAppender() {
        writerLogger().detachAppender(writerLogAppender);
        serviceLogger().detachAppender(serviceLogAppender);
    }

    private Logger writerLogger() {
        return (Logger) LoggerFactory.getLogger(AuditLogWriter.class);
    }

    private Logger serviceLogger() {
        return (Logger) LoggerFactory.getLogger(AuditLogService.class);
    }

    private UserPrincipal adminPrincipal() {
        return new UserPrincipal("000", "admin@millikart.az", "SYSTEM_ADMIN", null);
    }

    private UserPrincipal managerOfCompany2() {
        return new UserPrincipal("333", "manager@comp2.com", "COMPANY_MANAGER", "comp-02");
    }

    // 1. Успех записывается — с outcome и адресом клиента

    @Test
    public void successfulCreate_writesRecordWithSuccessOutcomeAndClientIp() throws Exception {
        createCompanyViaHttp("comp-01", "MilliKart LLC");

        List<AuditLog> records = auditLogs.findAll();
        assertThat(records).hasSize(1);
        AuditLog record = records.get(0);
        assertThat(record.getEntityType()).isEqualTo("COMPANY");
        assertThat(record.getEntityId()).isEqualTo("comp-01");
        assertThat(record.getAction()).isEqualTo("CREATE");
        assertThat(record.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        // Заголовка проксирования не было, поэтому клиентом считается сам peer MockMvc.
        assertThat(record.getClientIp()).isEqualTo("127.0.0.1");
    }

    // P3-2: смена статуса компании — отдельное событие BLOCK/UNBLOCK, как уже было для
    // пользователей и терминалов, а не строка внутри общего UPDATE, которую найдёшь лишь
    // полнотекстовым чтением журнала. Запись UPDATE с описанием полей при этом остаётся.
    @Test
    public void companyStatusChange_isItsOwnBlockAndUnblockEvent() throws Exception {
        createCompanyViaHttp("comp-01", "MilliKart LLC");
        auditLogs.deleteAll();

        mockMvc.perform(patch("/api/v1/companies/comp-01")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateCompanyRequest(null, "BLOCKED"))))
                .andExpect(status().isOk());

        List<AuditLog> afterBlock = auditLogs.findAll();
        assertThat(afterBlock).extracting(AuditLog::getAction).containsExactlyInAnyOrder("UPDATE", "BLOCK");
        AuditLog block = afterBlock.stream().filter(r -> "BLOCK".equals(r.getAction())).findFirst().orElseThrow();
        assertThat(block.getEntityType()).isEqualTo("COMPANY");
        assertThat(block.getEntityId()).isEqualTo("comp-01");
        assertThat(block.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(block.getDetails()).contains("BLOCKED", "was ACTIVE");

        auditLogs.deleteAll();
        mockMvc.perform(patch("/api/v1/companies/comp-01")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateCompanyRequest(null, "ACTIVE"))))
                .andExpect(status().isOk());

        assertThat(auditLogs.findAll()).extracting(AuditLog::getAction)
                .containsExactlyInAnyOrder("UPDATE", "UNBLOCK");

        // Повтор уже установленного статуса — не блокировка, выдумывать событие нельзя.
        auditLogs.deleteAll();
        mockMvc.perform(patch("/api/v1/companies/comp-01")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateCompanyRequest(null, "ACTIVE"))))
                .andExpect(status().isOk());
        assertThat(auditLogs.findAll()).extracting(AuditLog::getAction).containsExactly("UPDATE");
    }

    // 2. Откатанная операция не оставляет записи — ядро Р-35

    @Test
    public void rolledBackOperation_leavesNoAuditRecord() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            companyService.createCompany(new CreateCompanyRequest("comp-rb", "Rolled Back LLC"), adminPrincipal());
            // Событие аудита уже опубликовано; теперь операция падает.
            status.setRollbackOnly();
        });

        assertThat(companyRepository.existsById("comp-rb")).as("operation must be rolled back").isFalse();
        assertThat(auditLogs.findAll())
                .as("the journal must not describe an action that did not happen")
                .isEmpty();
    }

    // Тот же откат, но как в P2-8: транзакция падает на коммите, когда метод сервиса вместе с
    // вызовом аудита уже вернулся. Простой REQUIRES_NEW внутри logAction записал бы это
    // несостоявшееся создание.
    @Test
    public void commitTimeFailure_afterAuditEventPublished_leavesNoAuditRecord() throws Exception {
        // Проходит @NotBlank, но переполняет колонку varchar(255) на flush, то есть на коммите.
        String overlongName = "X".repeat(300);
        mockMvc.perform(post("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCompanyRequest("comp-long", overlongName))))
                .andExpect(status().is5xxServerError());

        assertThat(companyRepository.existsById("comp-long")).isFalse();
        assertThat(auditLogs.findAll()).isEmpty();
    }

    // 3. Отказ записывается, а операция остаётся отклонённой

    @Test
    public void deniedUpdate_isRecordedAsDenied_andOperationStaysRefused() throws Exception {
        createCompanyViaHttp("comp-01", "MilliKart LLC");
        auditLogs.deleteAll();

        mockMvc.perform(patch("/api/v1/companies/comp-01")
                        .header(HttpHeaders.AUTHORIZATION, managerTokenCompany2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateCompanyRequest("Hijacked LLC", null))))
                .andExpect(status().isForbidden());

        assertThat(companyRepository.findById("comp-01").orElseThrow().getName())
                .as("the refused change must not be applied")
                .isEqualTo("MilliKart LLC");

        List<AuditLog> records = auditLogs.findAll();
        assertThat(records).hasSize(1);
        AuditLog record = records.get(0);
        assertThat(record.getOutcome()).isEqualTo(AuditOutcome.DENIED);
        assertThat(record.getEntityType()).isEqualTo("COMPANY");
        assertThat(record.getEntityId()).isEqualTo("comp-01");
        assertThat(record.getAction()).isEqualTo("UPDATE");
        assertThat(record.getPerformedBy()).isEqualTo("manager@comp2.com");
        assertThat(record.getClientIp()).isEqualTo("127.0.0.1");
    }

    // Отказ в чтении журнала пишется изнутри AuditLogService, где окружающая транзакция
    // listAuditLogs — readOnly и вот-вот откатится отказом. Голый this.logDenied мимо прокси
    // (@Transactional(REQUIRES_NEW) вместо TransactionTemplate) уедет с этим откатом молча.
    // Покрыты обе ветки отказа listAuditLogs.
    @Test
    public void deniedListByEmployee_isRecorded() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, employeeTokenCompany1))
                .andExpect(status().isForbidden());

        List<AuditLog> records = auditLogs.findAll();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getOutcome()).isEqualTo(AuditOutcome.DENIED);
        assertThat(records.get(0).getEntityType()).isEqualTo("AUDIT_LOG");
        assertThat(records.get(0).getAction()).isEqualTo("LIST");
        assertThat(records.get(0).getPerformedBy()).isEqualTo("employee@comp1.com");
    }

    @Test
    public void deniedListByHeadWithoutCompany_isRecorded() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, headTokenWithoutCompany))
                .andExpect(status().isForbidden());

        assertThat(auditLogs.findAll())
                .singleElement()
                .extracting(AuditLog::getOutcome)
                .isEqualTo(AuditOutcome.DENIED);
    }

    // Строки записи об отказе контролирует вызывающий, а колонки ограничены (details
    // varchar(4000), entity_id varchar(255)), и выше по стеку их не режет никто. Без обрезки
    // вставка падает, отказ превращается в 500, и запись, ради которой всё делалось, не пишется:
    // любой аутентифицированный пользователь выключал бы аудит отказов набивкой поля.
    @Test
    public void deniedCreate_withOversizedInput_stillReturns403AndIsRecorded() throws Exception {
        String overlongName = "X".repeat(5000);

        mockMvc.perform(post("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, employeeTokenCompany1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCompanyRequest("Z".repeat(400), overlongName))))
                .andExpect(status().isForbidden());

        List<AuditLog> records = auditLogs.findAll();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getOutcome()).isEqualTo(AuditOutcome.DENIED);
        assertThat(records.get(0).getDetails()).hasSizeLessThanOrEqualTo(4000);
        assertThat(records.get(0).getEntityId()).hasSizeLessThanOrEqualTo(255);
    }

    // Запись об отказе подшита под компанию актора, а не под названную в запросе: иначе любой
    // пользователь писал бы произвольный текст в аудит чужого тенанта, просто назвав его id —
    // журнал скоупится по companyId для COMPANY_HEAD и COMPANY_MANAGER. Что пытались сделать,
    // остаётся видно в entityId и details.
    @Test
    public void deniedCreate_isFiledUnderActorsCompany_notTheRequestedOne() throws Exception {
        mockMvc.perform(post("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, employeeTokenCompany1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCompanyRequest("comp-victim", "Fabricated entry"))))
                .andExpect(status().isForbidden());

        List<AuditLog> records = auditLogs.findAll();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getCompanyId())
                .as("the record must not land in the audit view of the company named by the caller")
                .isEqualTo("comp-01");
        assertThat(records.get(0).getEntityId())
                .as("what was attempted stays visible")
                .isEqualTo("comp-victim");
    }

    // 3a. Значение вне словаря пишется как есть, только с предупреждением

    // Словарь (P3-2) держится предупреждением, а не исключением: запись аудита идёт внутри login,
    // возвратов и отказов, и опечатка в имени события не должна валить ни одну из них — журнал
    // ломал бы то, что журналирует. Значение пишется как есть, чтобы не потерять улику, а маркер
    // делает дрейф видимым мониторингу.
    @Test
    public void valueOutsideDictionary_isWrittenAsIs_andWarnedWithMarker() {
        assertThatCode(() -> auditLogService.logDenied("SOMETHING_ELSE", "id-1", "FROB",
                "admin@millikart.az", null, "dictionary drift"))
                .doesNotThrowAnyException();

        List<AuditLog> records = auditLogs.findAll();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getEntityType()).isEqualTo("SOMETHING_ELSE");
        assertThat(records.get(0).getAction()).isEqualTo("FROB");

        assertThat(serviceLogAppender.list)
                .as("both the unknown entityType and the unknown action must be reported")
                .filteredOn(event -> event.getLevel() == Level.WARN
                        && event.getFormattedMessage().contains(AuditLogService.AUDIT_OUTSIDE_DICTIONARY_MARKER))
                .anyMatch(event -> event.getFormattedMessage().contains("SOMETHING_ELSE"))
                .anyMatch(event -> event.getFormattedMessage().contains("FROB"));
    }

    // 4. Запись об отказе переживает откат окружающей транзакции

    @Test
    public void deniedRecord_survivesRollbackOfSurroundingTransaction() throws Exception {
        createCompanyViaHttp("comp-01", "MilliKart LLC");
        auditLogs.deleteAll();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            assertThatThrownBy(() -> companyService.updateCompany("comp-01",
                    new UpdateCompanyRequest("Hijacked LLC", null), managerOfCompany2()))
                    .isInstanceOf(InvalidStateException.class);
            status.setRollbackOnly();
        });

        List<AuditLog> records = auditLogs.findAll();
        assertThat(records)
                .as("the DENIED record is written in its own transaction and must survive the rollback")
                .hasSize(1);
        assertThat(records.get(0).getOutcome()).isEqualTo(AuditOutcome.DENIED);
    }

    // 5. Сбой записи в журнал не должен валить бизнес-операцию

    @Test
    public void auditWriteFailure_doesNotBreakBusinessOperation() throws Exception {
        doThrow(new DataAccessResourceFailureException("audit storage is down"))
                .when(auditLogRepository).save(any(AuditLog.class));

        mockMvc.perform(post("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCompanyRequest("comp-01", "MilliKart LLC"))))
                .andExpect(status().isCreated());

        assertThat(companyRepository.existsById("comp-01"))
                .as("the operation itself must have committed")
                .isTrue();
        assertThat(writerLogAppender.list)
                .as("the lost record must be reported with the monitoring marker")
                .anyMatch(event -> event.getLevel() == Level.ERROR
                        && event.getFormattedMessage().contains(AuditLogService.AUDIT_WRITE_FAILED_MARKER));
    }

    // Та же гарантия на пути отказа, где она значит другое: там журнал пишется до броска отказа,
    // поэтому вылетевшее исключение подменило бы законный 403 на 500.
    @Test
    public void auditWriteFailure_onDeniedPath_stillReturns403() throws Exception {
        doThrow(new DataAccessResourceFailureException("audit storage is down"))
                .when(auditLogRepository).save(any(AuditLog.class));

        mockMvc.perform(post("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, employeeTokenCompany1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCompanyRequest("comp-01", "MilliKart LLC"))))
                .andExpect(status().isForbidden());

        assertThat(serviceLogAppender.list)
                .as("the lost denial must be reported with the monitoring marker")
                .anyMatch(event -> event.getLevel() == Level.ERROR
                        && event.getFormattedMessage().contains(AuditLogService.AUDIT_WRITE_FAILED_MARKER));
    }

    // 6. Адрес клиента подчиняется правилам доверия ClientIp

    @Test
    public void clientIp_headerFromTrustedProxy_isBelieved() throws Exception {
        // Peer у MockMvc — 127.0.0.1, он в списке доверенных, поэтому ответ берётся из X-Real-IP.
        mockMvc.perform(post("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .header("X-Real-IP", "198.51.100.7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCompanyRequest("comp-01", "MilliKart LLC"))))
                .andExpect(status().isCreated());

        assertThat(auditLogs.findAll())
                .singleElement()
                .extracting(AuditLog::getClientIp)
                .isEqualTo("198.51.100.7");
    }

    @Test
    public void clientIp_headerFromUntrustedPeer_isIgnored() throws Exception {
        mockMvc.perform(post("/api/v1/companies")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.9");
                            return request;
                        })
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .header("X-Real-IP", "198.51.100.7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCompanyRequest("comp-01", "MilliKart LLC"))))
                .andExpect(status().isCreated());

        assertThat(auditLogs.findAll())
                .singleElement()
                .extracting(AuditLog::getClientIp)
                .isEqualTo("203.0.113.9");
    }

    // 7. Вне запроса адрес просто отсутствует, а не даёт ошибку

    @Test
    public void directServiceCall_outsideRequest_recordsWithoutClientIp() {
        assertThatCode(() -> companyService.createCompany(
                new CreateCompanyRequest("comp-01", "MilliKart LLC"), adminPrincipal()))
                .doesNotThrowAnyException();

        List<AuditLog> records = auditLogs.findAll();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getClientIp()).isNull();
        assertThat(records.get(0).getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
    }

    // Фикстуры

    private void createCompanyViaHttp(String id, String name) throws Exception {
        mockMvc.perform(post("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCompanyRequest(id, name))))
                .andExpect(status().isCreated());
    }
}
