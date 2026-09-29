package az.millikart.directory;

import static az.millikart.directory.DirectoryTestFixtures.company;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.millikart.directory.domain.Terminal;
import az.millikart.directory.domain.TerminalStatus;
import az.millikart.directory.dto.CreateCompanyRequest;
import az.millikart.directory.dto.CreateTerminalRequest;
import az.millikart.directory.dto.UpdateCompanyRequest;
import az.millikart.directory.dto.UpdateTerminalRequest;
import az.millikart.directory.repository.CompanyRepository;
import az.millikart.directory.repository.TerminalRepository;
import az.millikart.common.audit.AuditLog;
import az.millikart.common.security.CredentialCipher;
import az.millikart.common.security.JwtProvider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public class DirectoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private TerminalRepository terminalRepository;

    @Autowired
    private AuditLogTestRepository auditLogRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CredentialCipher credentialCipher;

    private String adminToken;
    private String headTokenCompany1;
    private String headTokenCompany2;
    private String managerTokenCompany1;
    private String employeeTokenCompany1;
    private String auditorToken;
    private String unknownRoleToken;

    @BeforeEach
    public void setup() {
        auditLogRepository.deleteAll();
        terminalRepository.deleteAll();
        companyRepository.deleteAll();
        DirectoryTestFixtures.providerLogins(jdbcTemplate, "comp-01", "comp-02", "comp-03", "new-login");

        adminToken = "Bearer " + jwtProvider.generateToken("000", "admin@millikart.az", "SYSTEM_ADMIN", null);
        headTokenCompany1 = "Bearer " + jwtProvider.generateToken("111", "head@comp1.com", "COMPANY_HEAD", "comp-01");
        headTokenCompany2 = "Bearer " + jwtProvider.generateToken("222", "head@comp2.com", "COMPANY_HEAD", "comp-02");
        managerTokenCompany1 = "Bearer " + jwtProvider.generateToken("333", "manager@comp1.com", "COMPANY_MANAGER", "comp-01");
        employeeTokenCompany1 = "Bearer " + jwtProvider.generateToken("444", "employee@comp1.com", "COMPANY_EMPLOYEE", "comp-01");
        auditorToken = "Bearer " + jwtProvider.generateToken("555", "auditor@millikart.az", "AUDITOR", null);
        // Role.fromValue не знает это значение, поэтому в сервисы принципал придёт с role == null.
        unknownRoleToken = "Bearer " + jwtProvider.generateToken("666", "hacker@comp1.com", "HACKER", "comp-01");
    }

    @Test
    public void testCompanyLifecycleAndAudit() throws Exception {
        CreateCompanyRequest createRequest = company("comp-01", "MilliKart LLC");
        mockMvc.perform(post("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is("comp-01")))
                .andExpect(jsonPath("$.name", is("MilliKart LLC")))
                .andExpect(jsonPath("$.createdBy", is("admin@millikart.az")))
                .andExpect(jsonPath("$.createdAt", notNullValue()));

        CreateCompanyRequest createRequest2 = company("comp-02", "Other LLC");
        mockMvc.perform(post("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, headTokenCompany1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest2)))
                .andExpect(status().isForbidden());

        UpdateCompanyRequest updateRequest = new UpdateCompanyRequest("MilliKart Global LLC", "ACTIVE", null, null);
        mockMvc.perform(patch("/api/v1/companies/comp-01")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("MilliKart Global LLC")))
                .andExpect(jsonPath("$.updatedBy", is("admin@millikart.az")));

        // Журнал постраничный с P2-2 и отдаётся от новых к старым с P2-5.
        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("entityType", "COMPANY")
                        .param("entityId", "comp-01")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2))) // CREATE + UPDATE
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.content[0].performedBy", is("admin@millikart.az")))
                .andExpect(jsonPath("$.content[0].action", is("UPDATE")))
                .andExpect(jsonPath("$.content[1].action", is("CREATE")));
    }

    // Правкой ставятся только ACTIVE и INACTIVE. DELETED через PATCH удалял компанию в обход удаления и его
    // записи в журнале, а незнакомое значение ни один экран не прочтёт; отказ ничего не меняет.
    @Test
    public void companyStatus_isOnlyActiveOrInactive() throws Exception {
        createCompany("comp-01", "MilliKart LLC");

        for (String status : List.of("DELETED", "BLOCKED", "active", " INACTIVE")) {
            mockMvc.perform(patch("/api/v1/companies/comp-01")
                            .header(HttpHeaders.AUTHORIZATION, adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new UpdateCompanyRequest("Renamed LLC", status, null, null))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", is("Company status must be ACTIVE or INACTIVE")));
        }
        assertThat(companyRepository.findById("comp-01").orElseThrow().getStatus()).isEqualTo("ACTIVE");
        assertThat(companyRepository.findById("comp-01").orElseThrow().getName()).isEqualTo("MilliKart LLC");

        mockMvc.perform(patch("/api/v1/companies/comp-01")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCompanyRequest(null, "INACTIVE", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("INACTIVE")));
        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("entityType", "COMPANY")
                        .param("entityId", "comp-01")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3))) // CREATE + UPDATE + BLOCK, отказы не пишутся
                .andExpect(jsonPath("$.content[?(@.action == 'BLOCK')]", hasSize(1)));
    }

    // Заводит терминалы только администратор (Р-80, Р-93): руководитель получает 403 до любых поисков по
    // справочнику, и тексты отказов не выдают, есть ли такой rid.
    @Test
    public void headCannotLinkAProviderTerminalByRid() throws Exception {
        mockMvc.perform(post("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(company("comp-01", "MilliKart LLC"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/terminals")
                        .header(HttpHeaders.AUTHORIZATION, headTokenCompany1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateTerminalRequest("comp-01", "E1120020"))))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testTerminalLifecycleAndRBAC() throws Exception {
        CreateCompanyRequest createComp = company("comp-01", "MilliKart LLC");
        mockMvc.perform(post("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createComp)))
                .andExpect(status().isCreated());

        DirectoryTestFixtures.companyTerminal(jdbcTemplate, "comp-01", "E1120020", "Main Terminal", "BS00001");
        String created = mockMvc.perform(post("/api/v1/terminals")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTerminalRequest("comp-01", "E1120020"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Main Terminal")))
                .andExpect(jsonPath("$.login", is("TerminalSys/BS00001")))
                .andExpect(jsonPath("$.terminalRid", is("TID-E1120020")))
                .andExpect(jsonPath("$.createdBy", is("admin@millikart.az")))
                .andReturn().getResponse().getContentAsString();
        int terminalId = objectMapper.readTree(created).get("id").asInt();

        DirectoryTestFixtures.providerTerminal(jdbcTemplate, "E1120021", "Unauthorized Terminal", "BS00002");
        CreateTerminalRequest createTerminalForbidden = new CreateTerminalRequest("comp-01", "E1120021");

        mockMvc.perform(post("/api/v1/terminals")
                        .header(HttpHeaders.AUTHORIZATION, headTokenCompany2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTerminalForbidden)))
                .andExpect(status().isForbidden());

        // Список терминалов постраничный с P2-1.
        mockMvc.perform(get("/api/v1/terminals")
                        .header(HttpHeaders.AUTHORIZATION, headTokenCompany1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].id", is(terminalId)));

        // Терминалы блокируются, а не удаляются (P2-8).
        mockMvc.perform(patch("/api/v1/terminals/" + terminalId)
                        .header(HttpHeaders.AUTHORIZATION, headTokenCompany1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateTerminalRequest(null, null, TerminalStatus.BLOCKED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("BLOCKED")));

        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("entityType", "TERMINAL")
                        .param("entityId", String.valueOf(terminalId))
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(2)))); // CREATE + UPDATE + BLOCK
    }

    // Регресс P0-3: у getTerminal стояло кэширование по одному #id, а проверка доступа была внутри
    // тела метода — первое законное чтение грело кэш, и все последующие чтения того же id тело
    // пропускали вместе с проверкой. Вернёшь ту аннотацию — второй запрос вернёт 200 с чужим
    // терминалом вместо 403.
    @Test
    public void getTerminal_afterAnotherCompanyFetchedIt_stillReturns403() throws Exception {
        createCompany("comp-01", "MilliKart LLC");
        createCompany("comp-02", "Other LLC");
        int terminalId = createTerminal("Cached Terminal", "comp-01", adminToken);

        // Владелец читает терминал — именно этот вызов грел кэш "terminals".
        mockMvc.perform(get("/api/v1/terminals/" + terminalId)
                        .header(HttpHeaders.AUTHORIZATION, headTokenCompany1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(terminalId)))
                .andExpect(jsonPath("$.companyId", is("comp-01")));

        // Чужая компания просит тот же id: значение горячее, но проверка обязана отработать.
        mockMvc.perform(get("/api/v1/terminals/" + terminalId)
                        .header(HttpHeaders.AUTHORIZATION, headTokenCompany2))
                .andExpect(status().isForbidden());
    }

    // Та же дыра с горячим кэшем, но на компаниях.
    @Test
    public void getCompany_afterAdminFetchedIt_stillReturns403ForForeignCompany() throws Exception {
        createCompany("comp-01", "MilliKart LLC");
        createCompany("comp-02", "Other LLC");

        mockMvc.perform(get("/api/v1/companies/comp-01")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("comp-01")));

        mockMvc.perform(get("/api/v1/companies/comp-01")
                        .header(HttpHeaders.AUTHORIZATION, headTokenCompany2))
                .andExpect(status().isForbidden());
    }

    // Р-81: номер терминала выдаёт база. Клиент, по старой памяти приславший свой id, не должен ни
    // упасть, ни занять выбранный номер — иначе два окна заведения спорили бы за один ключ.
    @Test
    public void createTerminal_numberIsAssignedByTheDatabase_andASentIdIsIgnored() throws Exception {
        createCompany("comp-01", "MilliKart LLC");

        int first = createTerminal("First Terminal", "comp-01", adminToken);
        DirectoryTestFixtures.companyTerminal(jdbcTemplate, "comp-01", "RID-SECOND", "Second Terminal", "BS00002");
        String body = mockMvc.perform(post("/api/v1/terminals")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id": 424242, "companyId": "comp-01", "merchantRid": "RID-SECOND"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int second = objectMapper.readTree(body).get("id").asInt();

        org.junit.jupiter.api.Assertions.assertNotEquals(424242, second);
        org.junit.jupiter.api.Assertions.assertTrue(second > first, "numbers come from one growing sequence");
    }

    // P1-15: для записи в терминалы нужна роль, а не только совпадающий companyId; заводит только
    // администратор (Р-93)

    @Test
    public void createTerminal_asEmployee_returns403() throws Exception {
        createCompany("comp-01", "MilliKart LLC");
        DirectoryTestFixtures.providerTerminal(jdbcTemplate, "RID-EMPLOYEE", "Employee Terminal", "BS00003");

        CreateTerminalRequest request = new CreateTerminalRequest("comp-01", "RID-EMPLOYEE");

        mockMvc.perform(post("/api/v1/terminals")
                        .header(HttpHeaders.AUTHORIZATION, employeeTokenCompany1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    public void updateTerminal_asEmployee_returns403() throws Exception {
        createCompany("comp-01", "MilliKart LLC");
        int terminalId = createTerminal("Main Terminal", "comp-01", adminToken);

        UpdateTerminalRequest request = new UpdateTerminalRequest("Renamed by employee", null, null);

        mockMvc.perform(patch("/api/v1/terminals/" + terminalId)
                        .header(HttpHeaders.AUTHORIZATION, employeeTokenCompany1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/terminals/" + terminalId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Main Terminal")));
    }

    @Test
    public void blockTerminal_asEmployee_returns403() throws Exception {
        createCompany("comp-01", "MilliKart LLC");
        int terminalId = createTerminal("Main Terminal", "comp-01", adminToken);

        mockMvc.perform(patch("/api/v1/terminals/" + terminalId)
                        .header(HttpHeaders.AUTHORIZATION, employeeTokenCompany1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateTerminalRequest(null, null, TerminalStatus.BLOCKED))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/terminals/" + terminalId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    // P2-8: эндпоинта нет, а путь есть. Терминал, через который прошёл платёж, удалить нельзя
    // вовсе (на него ссылаются ссылки), так что DELETE тут бессмыслен — но ресурс существует,
    // поэтому честный ответ 405, а 404 был бы враньём про URL.
    @Test
    public void deleteTerminal_isNotSupported_returns405() throws Exception {
        createCompany("comp-01", "MilliKart LLC");
        int terminalId = createTerminal("Main Terminal", "comp-01", adminToken);

        mockMvc.perform(delete("/api/v1/terminals/" + terminalId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(get("/api/v1/terminals/" + terminalId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());
    }

    // До Р-93 менеджер заводил терминал вручную, с логином и паролем. Ручного заведения больше нет:
    // терминал выбирается из справочника провайдера, а справочник видит только администратор.
    @Test
    public void createTerminal_asManager_returns403() throws Exception {
        createCompany("comp-01", "MilliKart LLC");
        DirectoryTestFixtures.providerTerminal(jdbcTemplate, "RID-MANAGER", "Manager Terminal", "BS00004");

        mockMvc.perform(post("/api/v1/terminals")
                        .header(HttpHeaders.AUTHORIZATION, managerTokenCompany1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTerminalRequest("comp-01", "RID-MANAGER"))))
                .andExpect(status().isForbidden());

        assertThat(terminalRepository.findByMerchantRid("RID-MANAGER")).isEmpty();
    }

    @Test
    public void createTerminal_withUnknownRole_returns403() throws Exception {
        createCompany("comp-01", "MilliKart LLC");

        CreateTerminalRequest request = new CreateTerminalRequest("comp-01", "RID-UNKNOWN");

        mockMvc.perform(post("/api/v1/terminals")
                        .header(HttpHeaders.AUTHORIZATION, unknownRoleToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    public void createTerminal_asAuditor_returns403() throws Exception {
        createCompany("comp-01", "MilliKart LLC");

        CreateTerminalRequest request = new CreateTerminalRequest("comp-01", "RID-AUDITOR");

        mockMvc.perform(post("/api/v1/terminals")
                        .header(HttpHeaders.AUTHORIZATION, auditorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // Чтение не тронуто: COMPANY_EMPLOYEE по-прежнему видит терминалы своей компании

    @Test
    public void listTerminals_asEmployee_returnsOwnCompanyTerminals() throws Exception {
        createCompany("comp-01", "MilliKart LLC");
        createCompany("comp-02", "Other LLC");
        int own = createTerminal("Own Terminal", "comp-01", adminToken);
        createTerminal("Foreign Terminal", "comp-02", adminToken);

        mockMvc.perform(get("/api/v1/terminals")
                        .header(HttpHeaders.AUTHORIZATION, employeeTokenCompany1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(own)))
                .andExpect(jsonPath("$.content[0].companyId", is("comp-01")));
    }

    @Test
    public void getTerminal_asEmployee_returnsOwnCompanyTerminal() throws Exception {
        createCompany("comp-01", "MilliKart LLC");
        createCompany("comp-02", "Other LLC");
        int own = createTerminal("Own Terminal", "comp-01", adminToken);
        int foreign = createTerminal("Foreign Terminal", "comp-02", adminToken);

        mockMvc.perform(get("/api/v1/terminals/" + own)
                        .header(HttpHeaders.AUTHORIZATION, employeeTokenCompany1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(own)))
                .andExpect(jsonPath("$.companyId", is("comp-01")));

        mockMvc.perform(get("/api/v1/terminals/" + foreign)
                        .header(HttpHeaders.AUTHORIZATION, employeeTokenCompany1))
                .andExpect(status().isForbidden());
    }

    // Креды компании к провайдеру (Р-93)

    @Test
    public void createCompany_withoutProviderCredentials_isRejected() throws Exception {
        mockMvc.perform(post("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": \"comp-01\", \"name\": \"MilliKart LLC\"}"))
                .andExpect(status().isBadRequest());

        assertThat(companyRepository.existsById("comp-01")).isFalse();
    }

    // В базе — только шифротекст, в ответе пароля нет вовсе: «менять может, видеть нет».
    @Test
    public void providerPassword_isStoredEncrypted_andNeverReturned() throws Exception {
        String body = mockMvc.perform(post("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(company("comp-01", "MilliKart LLC"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.providerLogin", is("MultiMerchantSys/comp-01")))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("secret-comp-01").doesNotContain("providerPassword");
        String stored = companyRepository.findById("comp-01").orElseThrow().getProviderPassword();
        assertThat(stored).isNotEqualTo("secret-comp-01");
        assertThat(credentialCipher.decrypt(stored)).isEqualTo("secret-comp-01");
    }

    // Логин к провайдеру задаёт и видит только администратор; остальным компания отдаётся без него.
    @Test
    public void providerLogin_isShownOnlyToASystemAdmin() throws Exception {
        createCompany("comp-01", "MilliKart LLC");

        assertThat(providerLoginIn(mockMvc.perform(get("/api/v1/companies/comp-01")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()))
                .isEqualTo("MultiMerchantSys/comp-01");
        assertThat(providerLoginIn(mockMvc.perform(get("/api/v1/companies/comp-01")
                        .header(HttpHeaders.AUTHORIZATION, headTokenCompany1))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()))
                .isNull();
        String auditorList = mockMvc.perform(get("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, auditorToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(auditorList).doesNotContain("MultiMerchantSys/comp-01");
    }

    @Test
    public void providerLogin_isUniqueAcrossCompanies() throws Exception {
        createCompany("comp-01", "MilliKart LLC");
        createCompany("comp-02", "Other LLC");

        mockMvc.perform(post("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCompanyRequest("comp-03", "Third LLC", "MultiMerchantSys/comp-01", "x"))))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/v1/companies/comp-02")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerLogin\": \"MultiMerchantSys/comp-01\"}"))
                .andExpect(status().isConflict());

        assertThat(companyRepository.existsById("comp-03")).isFalse();
        assertThat(companyRepository.findById("comp-02").orElseThrow().getProviderLogin())
                .isEqualTo("MultiMerchantSys/comp-02");
    }

    // Смена кредов — обычный UPDATE компании; пароль ложится шифротекстом, в журнал — только факт смены.
    @Test
    public void providerCredentialsChange_isEncrypted_andAuditedWithoutThePassword() throws Exception {
        createCompany("comp-01", "MilliKart LLC");
        auditLogRepository.deleteAll();

        mockMvc.perform(patch("/api/v1/companies/comp-01")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerLogin\": \"MultiMerchantSys/new-login\", \"providerPassword\": \"new-secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerLogin", is("MultiMerchantSys/new-login")));

        String stored = companyRepository.findById("comp-01").orElseThrow().getProviderPassword();
        assertThat(credentialCipher.decrypt(stored)).isEqualTo("new-secret");
        List<AuditLog> updates = auditLogRepository.findAll().stream()
                .filter(record -> "UPDATE".equals(record.getAction()))
                .toList();
        assertThat(updates).singleElement().satisfies(record -> {
            assertThat(record.getDetails()).contains("Provider login changed from 'MultiMerchantSys/comp-01' to 'MultiMerchantSys/new-login'");
            assertThat(record.getDetails()).contains("Provider password changed").doesNotContain("new-secret");
        });
    }

    // Пустой пароль в правке — «не менять», а не «стереть»: форма правки шлёт пароль, только когда его ввели.
    @Test
    public void emptyProviderPassword_leavesTheStoredOneAlone() throws Exception {
        createCompany("comp-01", "MilliKart LLC");
        String before = companyRepository.findById("comp-01").orElseThrow().getProviderPassword();

        mockMvc.perform(patch("/api/v1/companies/comp-01")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Renamed LLC\", \"providerPassword\": \"\"}"))
                .andExpect(status().isOk());

        assertThat(companyRepository.findById("comp-01").orElseThrow().getProviderPassword()).isEqualTo(before);
    }

    // У терминала больше нет пароля (Р-93): ни поля в ответе, ни пути, который его отдавал.
    @Test
    public void terminalPassword_isGone() throws Exception {
        createCompany("comp-01", "MilliKart LLC");
        int terminalId = createTerminal("Main Terminal", "comp-01", adminToken);

        String body = mockMvc.perform(get("/api/v1/terminals/" + terminalId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("password");
        mockMvc.perform(get("/api/v1/terminals/" + terminalId + "/password")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound());
    }

    // Логин компании — только активный мультимерчант из слепка ecom (Р-94)

    @Test
    public void providerLogin_mustBeAMultiMerchantLogin() throws Exception {
        DirectoryTestFixtures.providerLogin(jdbcTemplate, "terminal-login", "Active", "Active", "M-1");

        mockMvc.perform(post("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCompanyRequest("comp-01", "MilliKart LLC", "TerminalSys/terminal-login", "x"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Provider login must be a multimerchant login: MultiMerchantSys/<login>")));

        assertThat(companyRepository.existsById("comp-01")).isFalse();
    }

    // Три отказа отличаются текстом: администратору нужно знать, идти ли к провайдеру или обновить справочник.
    @Test
    public void providerLogin_unknownInactiveOrWithoutMerchants_isRefused() throws Exception {
        DirectoryTestFixtures.providerLogin(jdbcTemplate, "blocked-login", "Blocked", "Active", "M-2");
        DirectoryTestFixtures.providerLogin(jdbcTemplate, "lonely-login", "Active", null, null);
        DirectoryTestFixtures.providerLogin(jdbcTemplate, "unlinked-login", "Active", "Blocked", "M-3");

        assertCompanyRefused("MultiMerchantSys/nobody", "is not in the synchronised list of multimerchant logins");
        assertCompanyRefused("MultiMerchantSys/blocked-login", "is not active at the provider");
        assertCompanyRefused("MultiMerchantSys/lonely-login", "has no active merchants at the provider");
        assertCompanyRefused("MultiMerchantSys/unlinked-login", "has no active merchants at the provider");
    }

    // Пустой слепок — ecom ещё не снимал логины или не развёрнут: проверить не по чему, и компания не
    // заводится (решение 24.09.2026).
    @Test
    public void emptyLoginSnapshot_refusesTheCompany() throws Exception {
        jdbcTemplate.update("DELETE FROM provider_logins");

        assertCompanyRefused("MultiMerchantSys/comp-01", "has not been synchronised yet");
    }

    // Смена логина проверяется так же, а правка без смены логина слепок не трогает: логин, пропавший у
    // провайдера после сохранения, правке названия не мешает.
    @Test
    public void loginChange_isChecked_butOtherEditsIgnoreTheSnapshot() throws Exception {
        createCompany("comp-01", "MilliKart LLC");
        jdbcTemplate.update("DELETE FROM provider_logins WHERE login = 'comp-01'");

        mockMvc.perform(patch("/api/v1/companies/comp-01")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Renamed LLC\", \"providerLogin\": \"MultiMerchantSys/comp-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Renamed LLC")));
        mockMvc.perform(patch("/api/v1/companies/comp-01")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerLogin\": \"MultiMerchantSys/nobody\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("is not in the synchronised list")));

        assertThat(companyRepository.findById("comp-01").orElseThrow().getProviderLogin())
                .isEqualTo("MultiMerchantSys/comp-01");
    }

    // Логин компании выбирается из справочника (Р-95): в списке только то, что пройдёт проверку при
    // сохранении, и ничего занятого — ни живой компанией, ни удалённой (уникальный индекс держит и её логин).
    @Test
    public void freeProviderLogins_listOnlyLoginsThatPassTheCheck_andAreNotTaken() throws Exception {
        jdbcTemplate.update("DELETE FROM provider_logins WHERE login = 'free-login'");
        jdbcTemplate.update("INSERT INTO provider_logins (login, login_status, link_status, merchant_rid, merchant_title) "
                + "VALUES ('free-login', 'Active', 'Active', 'M-A', 'Shop A'), ('free-login', 'Active', 'Active', 'M-B', 'Shop B'), "
                + "('free-login', 'Active', 'Blocked', 'M-C', 'Shop C')");
        DirectoryTestFixtures.providerLogin(jdbcTemplate, "blocked-login", "Blocked", "Active", "M-2");
        DirectoryTestFixtures.providerLogin(jdbcTemplate, "lonely-login", "Active", null, null);
        createCompany("comp-01", "MilliKart LLC");
        createCompany("comp-02", "Deleted LLC");
        mockMvc.perform(delete("/api/v1/companies/comp-02").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNoContent());

        String body = mockMvc.perform(get("/api/v1/companies/provider-logins")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> logins = objectMapper.readTree(body).findValuesAsText("login");
        assertThat(logins).contains("MultiMerchantSys/free-login", "MultiMerchantSys/comp-03")
                .doesNotContain("MultiMerchantSys/comp-01", "MultiMerchantSys/comp-02",
                        "MultiMerchantSys/blocked-login", "MultiMerchantSys/lonely-login");
        JsonNode free = null;
        for (JsonNode option : objectMapper.readTree(body)) {
            if ("MultiMerchantSys/free-login".equals(option.get("login").asText())) {
                free = option;
            }
        }
        assertThat(free).isNotNull();
        assertThat(free.get("merchants").toString()).isEqualTo("[\"Shop A\",\"Shop B\"]");
    }

    @Test
    public void freeProviderLogins_areForASystemAdminOnly() throws Exception {
        mockMvc.perform(get("/api/v1/companies/provider-logins")
                        .header(HttpHeaders.AUTHORIZATION, headTokenCompany1))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/companies/provider-logins")
                        .header(HttpHeaders.AUTHORIZATION, auditorToken))
                .andExpect(status().isForbidden());
    }

    // Терминал компании — только мерчант её логина мультимерчанта (Р-96)

    // Р-103: прямой запрос к API не заводит того, чего форма не предлагает, — терминал, выключенный у
    // провайдера, и терминал удалённой компании.
    @Test
    public void createTerminal_inactiveAtTheProviderOrForADeletedCompany_isRefused() throws Exception {
        createCompany("comp-01", "MilliKart LLC");
        DirectoryTestFixtures.providerTerminal(jdbcTemplate, "RID-OFF", "Off Shop", "OF00001", false);
        DirectoryTestFixtures.linkMerchant(jdbcTemplate, "comp-01", "RID-OFF");

        mockMvc.perform(post("/api/v1/terminals")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTerminalRequest("comp-01", "RID-OFF"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Provider terminal RID-OFF is not active at the provider")));

        DirectoryTestFixtures.companyTerminal(jdbcTemplate, "comp-01", "RID-ON", "On Shop", "ON00001");
        mockMvc.perform(delete("/api/v1/companies/comp-01")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/terminals")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTerminalRequest("comp-01", "RID-ON"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Company with ID 'comp-01' not found")));
        assertThat(terminalRepository.findByMerchantRid("RID-OFF")).isEmpty();
        assertThat(terminalRepository.findByMerchantRid("RID-ON")).isEmpty();
    }

    @Test
    public void createTerminal_ofAMerchantOutsideTheCompanyLogin_isRefused() throws Exception {
        createCompany("comp-01", "MilliKart LLC");
        DirectoryTestFixtures.providerTerminal(jdbcTemplate, "RID-FOREIGN", "Foreign Shop", "FS00001");

        mockMvc.perform(post("/api/v1/terminals")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTerminalRequest("comp-01", "RID-FOREIGN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("does not belong to the multimerchant login of company comp-01")));

        assertThat(terminalRepository.findByMerchantRid("RID-FOREIGN")).isEmpty();
    }

    // Перенос — по тем же правилам, что заведение (Р-96, Р-97): в компанию, с логином которой мерчант терминала
    // не связан, терминал не переходит — его ссылки ушли бы к провайдеру с чужими кредами.
    @Test
    public void moveTerminal_onlyToACompanyWhoseLoginKnowsItsMerchant() throws Exception {
        createCompany("comp-01", "MilliKart LLC");
        createCompany("comp-02", "Other LLC");
        int terminalId = createTerminal("Main Shop", "comp-01", adminToken);
        String merchantRid = terminalRepository.findById(terminalId).orElseThrow().getMerchantRid();

        mockMvc.perform(patch("/api/v1/terminals/" + terminalId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTerminalRequest("Renamed", "comp-02", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("not linked to the multimerchant login of that company")));
        assertThat(terminalRepository.findById(terminalId).orElseThrow().getCompanyId()).isEqualTo("comp-01");
        assertThat(terminalRepository.findById(terminalId).orElseThrow().getName()).isEqualTo("Main Shop");

        DirectoryTestFixtures.linkMerchant(jdbcTemplate, "comp-02", merchantRid);
        mockMvc.perform(patch("/api/v1/terminals/" + terminalId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTerminalRequest(null, "comp-02", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId", is("comp-02")));
    }

    // Терминал, заведённый руками до справочника, мерчанта не знает — сверить его с логином нечем, и перенос
    // закрыт. Правка без смены компании проверку не проходит вовсе.
    @Test
    public void moveTerminal_withoutAProviderMerchant_isRefused_butOtherEditsAreNot() throws Exception {
        createCompany("comp-01", "MilliKart LLC");
        createCompany("comp-02", "Other LLC");
        terminalRepository.saveAndFlush(Terminal.builder()
                .id(700401).name("Manual Shop").login("manual_login")
                .companyId("comp-01").status(TerminalStatus.ACTIVE)
                .createdBy("seeder").updatedBy("seeder").build());

        mockMvc.perform(patch("/api/v1/terminals/700401")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTerminalRequest(null, "comp-02", null))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/terminals/700401")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTerminalRequest("Manual Shop 2", "comp-01", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Manual Shop 2")));
        assertThat(terminalRepository.findById(700401).orElseThrow().getCompanyId()).isEqualTo("comp-01");
    }

    // В списке для формы — только активные терминалы мерчантов логина компании, ещё не заведённые у нас.
    @Test
    public void providerTerminals_forACompany_listOnlyItsFreeMerchants() throws Exception {
        createCompany("comp-01", "MilliKart LLC");
        DirectoryTestFixtures.companyTerminal(jdbcTemplate, "comp-01", "RID-FREE", "Free Shop", "FR00001");
        DirectoryTestFixtures.companyTerminal(jdbcTemplate, "comp-01", "RID-TAKEN", "Taken Shop", "TK00001");
        DirectoryTestFixtures.providerTerminal(jdbcTemplate, "RID-OFF", "Off Shop", "OF00001", false);
        DirectoryTestFixtures.linkMerchant(jdbcTemplate, "comp-01", "RID-OFF");
        DirectoryTestFixtures.providerTerminal(jdbcTemplate, "RID-FOREIGN", "Foreign Shop", "FS00001");
        mockMvc.perform(post("/api/v1/terminals")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTerminalRequest("comp-01", "RID-TAKEN"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/terminals/provider-terminals").param("companyId", "comp-01")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].rid", is("RID-FREE")))
                .andExpect(jsonPath("$[0].login", is("FR00001")))
                .andExpect(jsonPath("$[0].terminalRid", is("TID-RID-FREE")));
        mockMvc.perform(get("/api/v1/terminals/provider-terminals").param("companyId", "comp-01")
                        .header(HttpHeaders.AUTHORIZATION, headTokenCompany1))
                .andExpect(status().isForbidden());
    }

    // Фикстуры

    private void assertCompanyRefused(String providerLogin, String reason) throws Exception {
        mockMvc.perform(post("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCompanyRequest("comp-refused", "Refused LLC", providerLogin, "x"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString(reason)));
        assertThat(companyRepository.existsById("comp-refused")).as(providerLogin).isFalse();
    }

    private String providerLoginIn(String companyJson) throws Exception {
        JsonNode login = objectMapper.readTree(companyJson).path("providerLogin");
        return login.isMissingNode() || login.isNull() ? null : login.asText();
    }

    private void createCompany(String id, String name) throws Exception {
        mockMvc.perform(post("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(company(id, name))))
                .andExpect(status().isCreated());
    }

    // Номер терминала выдаёт база (Р-81): фикстура возвращает его, а не придумывает. Терминал выбирается
    // из справочника провайдера, поэтому сначала — строка справочника с этим названием (Р-93).
    private int createTerminal(String name, String companyId, String token) throws Exception {
        String rid = "RID-" + UUID.randomUUID().toString().substring(0, 8);
        DirectoryTestFixtures.companyTerminal(jdbcTemplate, companyId, rid, name, "term_login");
        CreateTerminalRequest request = new CreateTerminalRequest(companyId, rid);
        String body = mockMvc.perform(post("/api/v1/terminals")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asInt();
    }
}
