package az.millikart.directory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.millikart.common.audit.AuditAction;
import az.millikart.common.audit.AuditChain;
import az.millikart.common.audit.AuditEntity;
import az.millikart.common.audit.AuditLog;
import az.millikart.common.audit.AuditLogService;
import az.millikart.common.security.JwtProvider;
import az.millikart.common.testing.PostgresIntegrationTest;
import az.millikart.directory.dto.AuditIntegrityReport;
import az.millikart.directory.service.AuditIntegrityService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

// Р-138: цепочка журнала на настоящей базе. Запись через приложение получает звено; правка, удаление записи или
// звена и вставка мимо приложения находятся проверкой, а одновременные записи из многих потоков дают цепочку без
// дыр — блокировка головы пускает звенья по одному. Проверка — SYSTEM_ADMIN и AUDITOR, разрыв — UNRESOLVED.
@PostgresIntegrationTest
class AuditChainIntegrityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AuditLogService auditLogService;
    @Autowired private AuditIntegrityService integrityService;
    @Autowired private AuditLogTestRepository auditLogRepository;

    private String adminToken;

    @BeforeEach
    void startAnEmptyChain() {
        jdbcTemplate.update("DELETE FROM audit_chain");
        jdbcTemplate.update("DELETE FROM audit_logs");
        jdbcTemplate.update("UPDATE audit_chain_head SET last_seq = 0, last_hash = ? WHERE id = 1", AuditChain.GENESIS);
        adminToken = "Bearer " + jwtProvider.generateToken("000", "admin@millikart.az", "SYSTEM_ADMIN", null);
    }

    @Test
    void recordsWrittenByTheApplication_formAnIntactChain() throws Exception {
        write(5);

        JsonNode report = check(adminToken);
        assertThat(report.get("intact").asBoolean()).isTrue();
        assertThat(report.get("checkedRecords").asLong()).isEqualTo(5);
        assertThat(report.get("headSeq").asLong()).isEqualTo(5);
        assertThat(report.get("notCovered").asLong()).isZero();

        // Запись VERIFY первой проверки — тоже звено.
        assertThat(check(adminToken).get("checkedRecords").asLong()).isEqualTo(6);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'VERIFY' "
                + "AND outcome = 'SUCCESS'", Integer.class)).isEqualTo(2);
    }

    @Test
    void aChangedRecord_breaksTheChainAtItsSeq_andIsRecordedUnresolved() {
        write(3);
        jdbcTemplate.update("UPDATE audit_logs SET details = 'forged' WHERE id = ?", auditIdAt(2));

        AuditIntegrityReport report = integrityService.verifyScheduled();

        assertThat(report.intact()).isFalse();
        assertThat(report.problems()).extracting(AuditIntegrityReport.Problem::kind, AuditIntegrityReport.Problem::seq)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("RECORD_CHANGED", 2L));
        assertThat(jdbcTemplate.queryForObject("SELECT details FROM audit_logs WHERE action = 'VERIFY' AND outcome = 'UNRESOLVED'",
                String.class)).startsWith("Audit chain broken: RECORD_CHANGED at seq 2");
    }

    @Test
    void aDeletedRecord_aMissingLink_andATruncatedTail_areFound() {
        write(5);
        jdbcTemplate.update("DELETE FROM audit_logs WHERE id = ?", auditIdAt(2));
        jdbcTemplate.update("DELETE FROM audit_chain WHERE seq = 4");
        jdbcTemplate.update("DELETE FROM audit_chain WHERE seq = 5");

        AuditIntegrityReport report = integrityService.verifyScheduled();

        assertThat(report.problems()).extracting(AuditIntegrityReport.Problem::kind)
                .containsExactly("RECORD_DELETED", "HEAD_MISMATCH", "RECORDS_OUTSIDE_CHAIN");
    }

    @Test
    void aRecordInsertedAroundTheApplication_isFound() {
        write(2);
        auditLogRepository.saveAndFlush(AuditLog.builder().entityType(AuditEntity.USER).entityId("ghost")
                .action(AuditAction.DELETE).performedBy("intruder").build());

        AuditIntegrityReport report = integrityService.verifyScheduled();

        assertThat(report.unsealedRecords()).isEqualTo(1);
        assertThat(report.problems()).extracting(AuditIntegrityReport.Problem::kind).containsExactly("RECORDS_OUTSIDE_CHAIN");
    }

    // Звенья пишутся по одному: восемь потоков по десять записей — восемьдесят звеньев подряд, без дыр и разрывов.
    @Test
    void concurrentWriters_produceAGaplessIntactChain() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> writers = new ArrayList<>();
            for (int thread = 0; thread < 8; thread++) {
                writers.add(pool.submit(() -> write(10)));
            }
            for (Future<?> writer : writers) {
                writer.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        AuditIntegrityReport report = integrityService.verifyScheduled();
        assertThat(report.intact()).as(report.problems().toString()).isTrue();
        assertThat(report.headSeq()).isEqualTo(80);
        assertThat(report.checkedRecords()).isEqualTo(80);
    }

    @Test
    void theCheck_isForTheAdminAndTheAuditorOnly() throws Exception {
        String auditorToken = "Bearer " + jwtProvider.generateToken("555", "auditor@millikart.az", "AUDITOR", null);
        String headToken = "Bearer " + jwtProvider.generateToken("111", "head@comp1.com", "COMPANY_HEAD", "comp-01");

        assertThat(check(auditorToken).get("intact").asBoolean()).isTrue();
        mockMvc.perform(post("/api/v1/audit-logs/integrity-checks").header(HttpHeaders.AUTHORIZATION, headToken))
                .andExpect(status().isForbidden());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'VERIFY' "
                + "AND outcome = 'DENIED'", Integer.class)).isEqualTo(1);
    }

    private void write(int count) {
        for (int i = 0; i < count; i++) {
            auditLogService.logDenied(AuditEntity.USER, UUID.randomUUID().toString(), AuditAction.UPDATE,
                    "head@comp1.com", "comp-01", "Denied: chain fixture " + i);
        }
    }

    private UUID auditIdAt(long seq) {
        return jdbcTemplate.queryForObject("SELECT audit_id FROM audit_chain WHERE seq = ?", UUID.class, seq);
    }

    private JsonNode check(String token) throws Exception {
        String body = mockMvc.perform(post("/api/v1/audit-logs/integrity-checks").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }
}
