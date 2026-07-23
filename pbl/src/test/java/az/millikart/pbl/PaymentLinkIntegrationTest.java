package az.millikart.pbl;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.millikart.common.security.JwtProvider;
import az.millikart.pbl.domain.PaymentLink;
import az.millikart.pbl.domain.PaymentLinkStatus;
import az.millikart.pbl.domain.Terminal;
import az.millikart.pbl.domain.Transaction;
import az.millikart.pbl.domain.TransactionStatus;
import az.millikart.pbl.repository.PaymentLinkRepository;
import az.millikart.pbl.repository.TerminalRepository;
import az.millikart.pbl.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentLinkIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private PaymentLinkRepository paymentLinkRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TerminalRepository terminalRepository;

    private String adminToken;
    private String headToken;
    private String employeeToken;
    private String auditorToken;
    private String foreignToken;

    @BeforeEach
    void cleanUp() {
        transactionRepository.deleteAll();
        paymentLinkRepository.deleteAll();
        terminalRepository.deleteAll();

        // Setup test terminal configurations
        Terminal terminal = Terminal.builder()
                .id(123456789)
                .name("Test Terminal")
                .login("TerminalSys/Admin")
                .password("1234")
                .companyId("test-company")
                .build();
        terminalRepository.save(terminal);

        // Generate tokens
        adminToken = createMockJwtToken("admin-user", "SYSTEM_ADMIN", null);
        headToken = createMockJwtToken("head-user", "COMPANY_HEAD", "test-company");
        employeeToken = createMockJwtToken("emp-user", "COMPANY_EMPLOYEE", "test-company");
        auditorToken = createMockJwtToken("aud-user", "AUDITOR", "test-company");
        foreignToken = createMockJwtToken("other-user", "COMPANY_HEAD", "other-company");
    }

    private String createMockJwtToken(String userId, String role, String companyId) {
        return "Bearer " + jwtProvider.generateToken(userId, userId + "@test.com", role, companyId);
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, token);
    }

    private ObjectNode validCreateRequest() {
        ObjectNode customer = objectMapper.createObjectNode();
        customer.put("fullName", "John Doe");
        customer.put("email", "test@test.com");
        customer.put("phone", "994509771884");

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("campaign", "summer_sale");

        ObjectNode request = objectMapper.createObjectNode();
        request.put("merchantOrderId", "ORDER-12345");
        request.put("terminal", 123456789);
        request.put("amount", new BigDecimal("1500.50"));
        request.put("currency", "AZN");
        request.put("description", "Payment for order #123456");
        request.set("customer", customer);
        request.put("paymentType", "DMS");
        request.put("usageType", "MULTIPLE");
        request.put("maxPayments", 25);
        request.set("metadata", metadata);
        return request;
    }

    private ObjectNode smsSingleCreateRequest() {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("merchantOrderId", "ORDER-SMS-1");
        request.put("terminal", 123456789);
        request.put("amount", new BigDecimal("1500.50"));
        request.put("currency", "AZN");
        request.put("paymentType", "SMS");
        request.put("usageType", "SINGLE");
        return request;
    }

    @Test
    void createPaymentLink_returns201WithBody() throws Exception {
        mockMvc.perform(authed(post("/api/v1/payment-links"), employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.terminal", is(123456789)))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.link", containsString("/open")));
    }

    @Test
    void createPaymentLink_asAuditor_returns403() throws Exception {
        mockMvc.perform(authed(post("/api/v1/payment-links"), auditorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void createPaymentLink_companyMismatch_returns403() throws Exception {
        mockMvc.perform(authed(post("/api/v1/payment-links"), foreignToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void createPaymentLink_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/payment-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPaymentLink_missingTerminal_returns400() throws Exception {
        ObjectNode request = validCreateRequest();
        request.remove("terminal");

        mockMvc.perform(authed(post("/api/v1/payment-links"), headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPaymentLink_returnsLink() throws Exception {
        UUID id = createLinkAndGetId(headToken);

        mockMvc.perform(authed(get("/api/v1/payment-links/{id}", id), employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id.toString())))
                .andExpect(jsonPath("$.amount", is(1500.50)));
    }

    @Test
    void getPaymentLink_companyMismatch_returns403() throws Exception {
        UUID id = createLinkAndGetId(headToken);

        mockMvc.perform(authed(get("/api/v1/payment-links/{id}", id), foreignToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void listPaymentLinks_returnsPagedResult() throws Exception {
        createLinkAndGetId(headToken);

        mockMvc.perform(authed(get("/api/v1/payment-links"), headToken)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    void listPaymentLinks_forForeignCompany_returnsEmpty() throws Exception {
        createLinkAndGetId(headToken);

        mockMvc.perform(authed(get("/api/v1/payment-links"), foreignToken)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    @Test
    void updatePaymentLink_appliesChanges() throws Exception {
        UUID id = createLinkAndGetId(headToken);

        ObjectNode update = objectMapper.createObjectNode();
        update.put("amount", new BigDecimal("1600.00"));
        update.put("status", "CANCELED");

        mockMvc.perform(authed(patch("/api/v1/payment-links/{id}", id), employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount", is(1600.00)))
                .andExpect(jsonPath("$.status", is("CANCELED")));
    }

    @Test
    void openPaymentLink_redirectsToProvider() throws Exception {
        UUID id = createLinkAndGetId(headToken);

        // Public endpoint, no auth needed
        mockMvc.perform(get("/api/v1/payment-links/{id}/open", id))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("rid=")));
    }

    @Test
    void completeDmsAndRefundFlow() throws Exception {
        UUID id = createLinkAndGetId(headToken);

        // Open to create a pending transaction.
        mockMvc.perform(get("/api/v1/payment-links/{id}/open", id))
                .andExpect(status().isFound());

        List<Transaction> transactions = transactionRepository.findAll();
        Transaction pending = transactions.getFirst();

        // Put it in AUTHORIZED status to allow DMS completion
        pending.setStatus(TransactionStatus.AUTHORIZED);
        transactionRepository.save(pending);

        ObjectNode complete = objectMapper.createObjectNode();
        complete.put("amount", new BigDecimal("1500.50"));

        mockMvc.perform(authed(post("/api/v1/transactions/{id}/complete", pending.getId()), employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(complete)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")));

        // Employee should NOT be able to refund
        ObjectNode refund = objectMapper.createObjectNode();
        refund.put("amount", new BigDecimal("500.00"));
        refund.put("reason", "Customer request");

        mockMvc.perform(authed(post("/api/v1/transactions/{id}/refund", pending.getId()), employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refund)))
                .andExpect(status().isForbidden());

        // Head/Admin SHOULD be able to refund
        mockMvc.perform(authed(post("/api/v1/transactions/{id}/refund", pending.getId()), headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refund)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PARTIALLY_REFUNDED")))
                .andExpect(jsonPath("$.amount", is(500.00)));
    }

    @Test
    void smsPayment_statusCheckMovesTransactionAndLinkToTerminalState() throws Exception {
        String response = mockMvc.perform(authed(post("/api/v1/payment-links"), headToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(smsSingleCreateRequest())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(response).get("id").asText());

        mockMvc.perform(get("/api/v1/payment-links/{id}/open", id))
                .andExpect(status().isFound());

        Transaction pending = transactionRepository.findAll().getFirst();

        // Redirect page checks status (without auth token)
        mockMvc.perform(get("/api/v1/transactions/{providerOrderId}/status", pending.getProviderOrderId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")));

        Transaction settled = transactionRepository.findById(pending.getId()).orElseThrow();
        Assertions.assertEquals(TransactionStatus.SUCCESS, settled.getStatus());

        PaymentLink link = paymentLinkRepository.findById(id).orElseThrow();
        Assertions.assertEquals(PaymentLinkStatus.COMPLETED, link.getStatus());
    }

    private UUID createLinkAndGetId(String token) throws Exception {
        String response = mockMvc.perform(authed(post("/api/v1/payment-links"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }
}
