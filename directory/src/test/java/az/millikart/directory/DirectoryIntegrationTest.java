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

import az.millikart.directory.dto.CreateCompanyRequest;
import az.millikart.directory.dto.CreateTerminalRequest;
import az.millikart.directory.dto.UpdateCompanyRequest;
import az.millikart.directory.repository.AuditLogRepository;
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
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String headTokenCompany1;
    private String headTokenCompany2;

    @BeforeEach
    public void setup() {
        auditLogRepository.deleteAll();
        terminalRepository.deleteAll();
        companyRepository.deleteAll();

        // Generate tokens
        adminToken = "Bearer " + jwtProvider.generateToken("000", "admin@millikart.az", "SYSTEM_ADMIN", null);
        headTokenCompany1 = "Bearer " + jwtProvider.generateToken("111", "head@comp1.com", "COMPANY_HEAD", "comp-01");
        headTokenCompany2 = "Bearer " + jwtProvider.generateToken("222", "head@comp2.com", "COMPANY_HEAD", "comp-02");
    }

    @Test
    public void testCompanyLifecycleAndAudit() throws Exception {
        // 1. Create Company as SYSTEM_ADMIN
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

        // 2. Non-admin cannot create company
        CreateCompanyRequest createRequest2 = new CreateCompanyRequest("comp-02", "Other LLC");
        mockMvc.perform(post("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, headTokenCompany1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest2)))
                .andExpect(status().isForbidden());

        // 3. Update Company as SYSTEM_ADMIN
        UpdateCompanyRequest updateRequest = new UpdateCompanyRequest("MilliKart Global LLC", "ACTIVE");
        mockMvc.perform(patch("/api/v1/companies/comp-01")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("MilliKart Global LLC")))
                .andExpect(jsonPath("$.updatedBy", is("admin@millikart.az")));

        // 4. Verify Audit Logs for Company
        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("entityType", "COMPANY")
                        .param("entityId", "comp-01")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2))) // CREATE + UPDATE
                .andExpect(jsonPath("$[0].performedBy", is("admin@millikart.az")))
                .andExpect(jsonPath("$[0].action", is("CREATE")))
                .andExpect(jsonPath("$[1].action", is("UPDATE")));
    }

    @Test
    public void testTerminalLifecycleAndRBAC() throws Exception {
        // Create Company comp-01 first
        CreateCompanyRequest createComp = new CreateCompanyRequest("comp-01", "MilliKart LLC");
        mockMvc.perform(post("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createComp)))
                .andExpect(status().isCreated());

        // 1. Create Terminal as COMPANY_HEAD of comp-01 (Success)
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

        // 2. Try creating Terminal as COMPANY_HEAD of comp-02 for comp-01 (Forbidden / BadRequest)
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

        // 3. List terminals as COMPANY_HEAD of comp-01 (returns 1 terminal)
        mockMvc.perform(get("/api/v1/terminals")
                        .header(HttpHeaders.AUTHORIZATION, headTokenCompany1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(998877)));

        // 4. Delete Terminal as COMPANY_HEAD of comp-01
        mockMvc.perform(delete("/api/v1/terminals/998877")
                        .header(HttpHeaders.AUTHORIZATION, headTokenCompany1))
                .andExpect(status().isNoContent());

        // 5. Verify Terminal Audit Log
        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("entityType", "TERMINAL")
                        .param("entityId", "998877")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2)))); // CREATE + DELETE
    }
}
