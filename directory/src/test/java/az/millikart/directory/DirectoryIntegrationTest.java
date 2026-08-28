package az.millikart.directory;

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

import az.millikart.directory.domain.TerminalStatus;
import az.millikart.directory.dto.CreateCompanyRequest;
import az.millikart.directory.dto.CreateTerminalRequest;
import az.millikart.directory.dto.UpdateCompanyRequest;
import az.millikart.directory.dto.UpdateTerminalRequest;
import az.millikart.directory.repository.CompanyRepository;
import az.millikart.directory.repository.TerminalRepository;
import az.millikart.common.security.JwtProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
        CreateCompanyRequest createRequest = new CreateCompanyRequest("comp-01", "MilliKart LLC");
        mockMvc.perform(post("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is("comp-01")))
                .andExpect(jsonPath("$.name", is("MilliKart LLC")))
                .andExpect(jsonPath("$.createdBy", is("admin@millikart.az")))
                .andExpect(jsonPath("$.createdAt", notNullValue()));

        CreateCompanyRequest createRequest2 = new CreateCompanyRequest("comp-02", "Other LLC");
        mockMvc.perform(post("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, headTokenCompany1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest2)))
                .andExpect(status().isForbidden());

        UpdateCompanyRequest updateRequest = new UpdateCompanyRequest("MilliKart Global LLC", "ACTIVE");
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

    @Test
    public void testTerminalLifecycleAndRBAC() throws Exception {
        CreateCompanyRequest createComp = new CreateCompanyRequest("comp-01", "MilliKart LLC");
        mockMvc.perform(post("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createComp)))
                .andExpect(status().isCreated());

        CreateTerminalRequest createTerminal = new CreateTerminalRequest(
                998877,
                "Main Terminal",
                "term_login",
                "term_pass",
                "comp-01"
        );

        mockMvc.perform(post("/api/v1/terminals")
                        .header(HttpHeaders.AUTHORIZATION, headTokenCompany1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTerminal)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(998877)))
                .andExpect(jsonPath("$.createdBy", is("head@comp1.com")));

        CreateTerminalRequest createTerminalForbidden = new CreateTerminalRequest(
                998878,
                "Unauthorized Terminal",
                "term_login2",
                "term_pass2",
                "comp-01"
        );

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
                .andExpect(jsonPath("$.content[0].id", is(998877)));

        // Терминалы блокируются, а не удаляются (P2-8).
        mockMvc.perform(patch("/api/v1/terminals/998877")
                        .header(HttpHeaders.AUTHORIZATION, headTokenCompany1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateTerminalRequest(null, null, null, null, TerminalStatus.BLOCKED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("BLOCKED")));

        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("entityType", "TERMINAL")
                        .param("entityId", "998877")
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
        int terminalId = 700301;
        createTerminal(terminalId, "Cached Terminal", "comp-01", adminToken);

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

    // P1-15: для записи в терминалы нужна роль, а не только совпадающий companyId

    @Test
    public void createTerminal_asEmployee_returns403() throws Exception {
        createCompany("comp-01", "MilliKart LLC");

        CreateTerminalRequest request = new CreateTerminalRequest(
                700311, "Employee Terminal", "term_login", "term_pass", "comp-01");

        mockMvc.perform(post("/api/v1/terminals")
                        .header(HttpHeaders.AUTHORIZATION, employeeTokenCompany1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    public void updateTerminal_asEmployee_returns403() throws Exception {
        createCompany("comp-01", "MilliKart LLC");
        int terminalId = 700312;
        createTerminal(terminalId, "Main Terminal", "comp-01", adminToken);

        // Креды — ровно то, что сотрудник не должен уметь перевыпускать.
        UpdateTerminalRequest request = new UpdateTerminalRequest(null, "stolen_login", "stolen_pass", null, null);

        mockMvc.perform(patch("/api/v1/terminals/" + terminalId)
                        .header(HttpHeaders.AUTHORIZATION, employeeTokenCompany1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/terminals/" + terminalId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login", is("term_login")));
    }

    @Test
    public void blockTerminal_asEmployee_returns403() throws Exception {
        createCompany("comp-01", "MilliKart LLC");
        int terminalId = 700313;
        createTerminal(terminalId, "Main Terminal", "comp-01", adminToken);

        mockMvc.perform(patch("/api/v1/terminals/" + terminalId)
                        .header(HttpHeaders.AUTHORIZATION, employeeTokenCompany1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateTerminalRequest(null, null, null, null, TerminalStatus.BLOCKED))))
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
        int terminalId = 700317;
        createTerminal(terminalId, "Main Terminal", "comp-01", adminToken);

        mockMvc.perform(delete("/api/v1/terminals/" + terminalId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(get("/api/v1/terminals/" + terminalId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());
    }

    @Test
    public void createTerminal_asManager_returns201() throws Exception {
        createCompany("comp-01", "MilliKart LLC");

        CreateTerminalRequest request = new CreateTerminalRequest(
                700314, "Manager Terminal", "term_login", "term_pass", "comp-01");

        mockMvc.perform(post("/api/v1/terminals")
                        .header(HttpHeaders.AUTHORIZATION, managerTokenCompany1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(700314)))
                .andExpect(jsonPath("$.createdBy", is("manager@comp1.com")));
    }

    @Test
    public void createTerminal_withUnknownRole_returns403() throws Exception {
        createCompany("comp-01", "MilliKart LLC");

        CreateTerminalRequest request = new CreateTerminalRequest(
                700315, "Unknown Role Terminal", "term_login", "term_pass", "comp-01");

        mockMvc.perform(post("/api/v1/terminals")
                        .header(HttpHeaders.AUTHORIZATION, unknownRoleToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    public void createTerminal_asAuditor_returns403() throws Exception {
        createCompany("comp-01", "MilliKart LLC");

        CreateTerminalRequest request = new CreateTerminalRequest(
                700316, "Auditor Terminal", "term_login", "term_pass", "comp-01");

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
        createTerminal(700321, "Own Terminal", "comp-01", adminToken);
        createTerminal(700322, "Foreign Terminal", "comp-02", adminToken);

        mockMvc.perform(get("/api/v1/terminals")
                        .header(HttpHeaders.AUTHORIZATION, employeeTokenCompany1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(700321)))
                .andExpect(jsonPath("$.content[0].companyId", is("comp-01")));
    }

    @Test
    public void getTerminal_asEmployee_returnsOwnCompanyTerminal() throws Exception {
        createCompany("comp-01", "MilliKart LLC");
        createCompany("comp-02", "Other LLC");
        createTerminal(700331, "Own Terminal", "comp-01", adminToken);
        createTerminal(700332, "Foreign Terminal", "comp-02", adminToken);

        mockMvc.perform(get("/api/v1/terminals/700331")
                        .header(HttpHeaders.AUTHORIZATION, employeeTokenCompany1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(700331)))
                .andExpect(jsonPath("$.companyId", is("comp-01")));

        mockMvc.perform(get("/api/v1/terminals/700332")
                        .header(HttpHeaders.AUTHORIZATION, employeeTokenCompany1))
                .andExpect(status().isForbidden());
    }

    // Фикстуры

    private void createCompany(String id, String name) throws Exception {
        mockMvc.perform(post("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCompanyRequest(id, name))))
                .andExpect(status().isCreated());
    }

    private void createTerminal(int id, String name, String companyId, String token) throws Exception {
        CreateTerminalRequest request = new CreateTerminalRequest(id, name, "term_login", "term_pass", companyId);
        mockMvc.perform(post("/api/v1/terminals")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}
