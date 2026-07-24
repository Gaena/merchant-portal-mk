package az.millikart.auth;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.millikart.auth.domain.Company;
import az.millikart.auth.domain.User;
import az.millikart.auth.dto.CreateUserRequest;
import az.millikart.auth.dto.LoginRequest;
import az.millikart.auth.dto.UpdateUserRequest;
import az.millikart.auth.repository.CompanyRepository;
import az.millikart.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private final String fallbackToken = "Bearer pbl-secret-token";

    @BeforeEach
    public void setup() throws Exception {
        userRepository.deleteAll();
        companyRepository.deleteAll();

        // Seed default company
        Company company = Company.builder()
                .id("comp-01")
                .name("MilliKart LLC")
                .status("ACTIVE")
                .build();
        companyRepository.save(company);

        // Seed default admin
        User admin = User.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000000"))
                .username("admin@millikart.az")
                // Hash of "admin123"
                .passwordHash("$2a$10$C641UbM6EBi7lBaebQ4eo./gv2YJypc66lsKJzB2uM7q/Qoj1bSVO")
                .fullName("System Admin")
                .role("SYSTEM_ADMIN")
                .status("ACTIVE")
                .build();
        userRepository.save(admin);

        // Get admin token via login
        LoginRequest loginRequest = new LoginRequest("admin@millikart.az", "admin123");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        adminToken = "Bearer " + objectMapper.readTree(responseBody).get("token").asText();
    }

    @Test
    public void testLogin_Success() throws Exception {
        LoginRequest loginRequest = new LoginRequest("admin@millikart.az", "admin123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.role", is("SYSTEM_ADMIN")));
    }

    @Test
    public void testLogin_InvalidCredentials() throws Exception {
        LoginRequest loginRequest = new LoginRequest("admin@millikart.az", "wrongpass");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Invalid username or password")));
    }

    @Test
    public void testLogin_InvalidEmailFormat() throws Exception {
        LoginRequest loginRequest = new LoginRequest("not-an-email-address", "HeadPassword123!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Username must be a valid email address")));
    }

    @Test
    public void testUserCRUD_Success() throws Exception {
        // 1. Create a Company Head user as SYSTEM_ADMIN
        CreateUserRequest createHead = new CreateUserRequest(
                "head@comp01.com",
                "HeadPassword123!",
                "Company Head User",
                "COMPANY_HEAD",
                "comp-01"
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createHead)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", is("head@comp01.com")))
                .andExpect(jsonPath("$.role", is("COMPANY_HEAD")))
                .andExpect(jsonPath("$.companyId", is("comp-01")))
                .andReturn();

        String headIdStr = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();
        UUID headId = UUID.fromString(headIdStr);

        // 2. Get Head user token via login
        LoginRequest headLogin = new LoginRequest("head@comp01.com", "HeadPassword123!");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(headLogin)))
                .andExpect(status().isOk())
                .andReturn();

        String headToken = "Bearer " + objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("token").asText();

        // 3. Create Employee user as COMPANY_HEAD for the same company (success)
        CreateUserRequest createEmp = new CreateUserRequest(
                "emp@comp01.com",
                "EmployeePass123!",
                "Employee User",
                "COMPANY_EMPLOYEE",
                "comp-01"
        );

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createEmp)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", is("emp@comp01.com")))
                .andExpect(jsonPath("$.companyId", is("comp-01")));

        // 4. Try to create user for a different/non-existent company as COMPANY_HEAD (fail)
        CreateUserRequest createOtherCompanyEmp = new CreateUserRequest(
                "other@comp.com",
                "EmployeePass123!",
                "Other User",
                "COMPANY_EMPLOYEE",
                "comp-different"
        );

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createOtherCompanyEmp)))
                .andExpect(status().isForbidden());

        // 5. List users as COMPANY_HEAD (returns head and employee)
        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, headToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // 6. Update user
        UpdateUserRequest updateRequest = new UpdateUserRequest("Updated Name", null, "NewSecurePass123!", "ACTIVE");
        mockMvc.perform(patch("/api/v1/users/" + headId)
                        .header(HttpHeaders.AUTHORIZATION, headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName", is("Updated Name")));

        // 7. Login with updated password
        LoginRequest updatedLogin = new LoginRequest("head@comp01.com", "NewSecurePass123!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedLogin)))
                .andExpect(status().isOk());

        // 8. Delete user
        mockMvc.perform(delete("/api/v1/users/" + headId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNoContent());

        // 9. Verify user deleted (no longer found)
        mockMvc.perform(get("/api/v1/users/" + headId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testAccountLockout_After6FailedAttempts() throws Exception {
        LoginRequest invalidLogin = new LoginRequest("admin@millikart.az", "WrongPass123!");

        // 6 failed attempts
        for (int i = 1; i <= 6; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidLogin)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", is("Invalid username or password")));
        }

        // 7th attempt should be blocked due to PCI-DSS Account Lockout (30 mins)
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidLogin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Account is locked due to multiple failed login attempts. Please try again later.")));
    }
}
