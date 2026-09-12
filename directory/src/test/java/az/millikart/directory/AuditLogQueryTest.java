package az.millikart.directory;

import az.millikart.common.testing.PostgresTestContainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.millikart.common.security.JwtProvider;
import az.millikart.common.audit.AuditLog;
import az.millikart.directory.repository.CompanyRepository;
import az.millikart.directory.repository.TerminalRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

// Чтение после P2-2: фильтрация и постраничность — на стороне БД, порядок всегда от новых к
// старым, а скоуп по компании держится ровно так же, как прежний фильтр в памяти.
//
// Сортировка, срезы по времени и поиск через LIKE: у PostgreSQL здесь своя локаль и своя
// работа с временными типами, и проверять их на H2 значит проверять другую СУБД.
@SpringBootTest
@Import(PostgresTestContainer.class)
@AutoConfigureMockMvc
public class AuditLogQueryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogTestRepository auditLogRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private TerminalRepository terminalRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private String adminToken;
    private String auditorToken;
    private String headTokenCompany1;
    private String headTokenCompany2;

    @BeforeEach
    public void setup() {
        auditLogRepository.deleteAll();
        terminalRepository.deleteAll();
        companyRepository.deleteAll();

        adminToken = "Bearer " + jwtProvider.generateToken("000", "admin@millikart.az", "SYSTEM_ADMIN", null);
        auditorToken = "Bearer " + jwtProvider.generateToken("555", "auditor@millikart.az", "AUDITOR", null);
        headTokenCompany1 = "Bearer " + jwtProvider.generateToken("111", "head@comp1.com", "COMPANY_HEAD", "comp-01");
        headTokenCompany2 = "Bearer " + jwtProvider.generateToken("222", "head@comp2.com", "COMPANY_HEAD", "comp-02");
    }

    // Проверяется чтение, а не запись, поэтому запись кладётся напрямую. Пауза держит created_at
    // строго возрастающим даже на грубых часах, иначе порядок от новых к старым неоднозначен.
    private AuditLog seed(String entityType, String entityId, String action, String companyId) {
        AuditLog saved = auditLogRepository.saveAndFlush(AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .performedBy("seeder@test")
                .companyId(companyId)
                .build());
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return saved;
    }

    // 8. Фильтр в БД делает ровно то же, что делал фильтр в памяти

    @Test
    public void entityFilter_forCompanyHead_matchesFormerInMemoryFiltering() throws Exception {
        seed("TERMINAL", "777", "CREATE", "comp-01");
        seed("TERMINAL", "777", "UPDATE", "comp-01");
        seed("TERMINAL", "888", "CREATE", "comp-01");
        seed("COMPANY", "comp-01", "CREATE", "comp-01");
        seed("TERMINAL", "777", "CREATE", "comp-02");

        // Нижний регистр намеренно: прежний фильтр в памяти сравнивал через equalsIgnoreCase.
        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("entityType", "terminal")
                        .param("entityId", "777")
                        .header(HttpHeaders.AUTHORIZATION, headTokenCompany1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", Matchers.hasSize(2)))
                .andExpect(jsonPath("$.totalElements", Matchers.is(2)))
                .andExpect(jsonPath("$.content[0].companyId", Matchers.is("comp-01")))
                .andExpect(jsonPath("$.content[1].companyId", Matchers.is("comp-01")))
                .andExpect(jsonPath("$.content[0].action", Matchers.is("UPDATE")))
                .andExpect(jsonPath("$.content[1].action", Matchers.is("CREATE")));
    }

    // 9. Чужие записи не видны даже при точном совпадении entityId

    @Test
    public void companyHead_doesNotSeeForeignRecords_evenWithExactEntityId() throws Exception {
        seed("TERMINAL", "888", "CREATE", "comp-01");

        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("entityType", "TERMINAL")
                        .param("entityId", "888")
                        .header(HttpHeaders.AUTHORIZATION, headTokenCompany2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", Matchers.hasSize(0)))
                .andExpect(jsonPath("$.totalElements", Matchers.is(0)));
    }

    // 10. Постраничность: страницы не пересекаются, итоги верны

    @Test
    public void pagination_pagesAreDisjoint_andTotalsAreCorrect() throws Exception {
        for (int i = 0; i < 25; i++) {
            seed("TERMINAL", String.valueOf(1000 + i), "CREATE", "comp-01");
        }

        Set<String> firstPageIds = contentIds(page(0, 10));
        Set<String> secondPageIds = contentIds(page(1, 10));

        assertThat(firstPageIds).hasSize(10);
        assertThat(secondPageIds).hasSize(10);
        assertThat(firstPageIds).doesNotContainAnyElementsOf(secondPageIds);

        JsonNode firstPage = page(0, 10);
        assertThat(firstPage.get("totalElements").asLong()).isEqualTo(25);
        assertThat(firstPage.get("totalPages").asInt()).isEqualTo(3);
        assertThat(firstPage.get("number").asInt()).isEqualTo(0);
        assertThat(firstPage.get("size").asInt()).isEqualTo(10);
    }

    // 11. Порядок: всегда от новых к старым

    @Test
    public void order_isNewestFirst() throws Exception {
        seed("TERMINAL", "oldest", "CREATE", "comp-01");
        seed("TERMINAL", "middle", "CREATE", "comp-01");
        seed("TERMINAL", "newest", "CREATE", "comp-01");

        JsonNode content = page(0, 20).get("content");
        assertThat(content.get(0).get("entityId").asText()).isEqualTo("newest");
        assertThat(content.get(1).get("entityId").asText()).isEqualTo("middle");
        assertThat(content.get(2).get("entityId").asText()).isEqualTo("oldest");

        List<Instant> createdAts = new ArrayList<>();
        content.forEach(node -> createdAts.add(Instant.parse(node.get("createdAt").asText())));
        for (int i = 1; i < createdAts.size(); i++) {
            assertThat(createdAts.get(i - 1)).isAfterOrEqualTo(createdAts.get(i));
        }
    }

    // Параметры страницы зажимаются, а не передаются как есть: PageRequest.of бросает на page < 0
    // и size < 1, обработчика у этого исключения нет, и опечатка в query вернулась бы 500 со
    // стектрейсом. Верхняя граница нужна по другой причине: audit_logs только растёт, и size без
    // потолка — это просьба загрузить весь журнал в heap.
    @Test
    public void pagingParameters_areClamped_notRejectedWithAnError() throws Exception {
        for (int i = 0; i < 3; i++) {
            seed("TERMINAL", String.valueOf(2000 + i), "CREATE", "comp-01");
        }

        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("page", "-5")
                        .param("size", "0")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number", Matchers.is(0)))
                .andExpect(jsonPath("$.size", Matchers.is(1)));

        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("size", "2000000000")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", Matchers.is(200)));
    }

    // 12. AUDITOR видит записи всех компаний

    @Test
    public void auditor_seesRecordsOfAllCompanies() throws Exception {
        seed("TERMINAL", "111", "CREATE", "comp-01");
        seed("TERMINAL", "222", "CREATE", "comp-02");
        seed("COMPANY", "comp-03", "CREATE", null);

        mockMvc.perform(get("/api/v1/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", Matchers.hasSize(3)))
                .andExpect(jsonPath("$.totalElements", Matchers.is(3)));
    }

    // P3-1, D.1: регресс тихого «фильтра только парой» — entityType без entityId раньше
    // игнорировался и возвращался весь журнал. Теперь каждый из двух фильтрует сам по себе.
    @Test
    public void entityTypeAlone_andEntityIdAlone_eachFilterOnTheirOwn() throws Exception {
        seed("TERMINAL", "777", "CREATE", "comp-01");
        seed("COMPANY", "comp-01", "CREATE", "comp-01");

        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("entityType", "terminal")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", Matchers.is(1)))
                .andExpect(jsonPath("$.content[0].entityType", Matchers.is("TERMINAL")));

        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("entityId", "777")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", Matchers.is(1)))
                .andExpect(jsonPath("$.content[0].entityId", Matchers.is("777")));
    }

    // P3-1, D.3: одна операция пишет несколько событий в одну миллисекунду, так что одинаковый
    // created_at — норма, а не край. При сортировке только по createdAt порядок свой на каждый
    // запрос, и строка попадает то на две страницы, то ни на одну. Довесок id DESC делает порядок
    // полным: пять строк с одним временем при size=2 обязаны вернуться ровно по разу.
    @Test
    public void equalCreatedAt_doesNotDuplicateOrLoseRowsAcrossPages() throws Exception {
        Instant sameMoment = Instant.parse("2026-08-24T10:00:00Z");
        for (int i = 0; i < 5; i++) {
            seedAt("tie-" + i, sameMoment);
        }

        Set<String> seenIds = new HashSet<>();
        int rows = 0;
        for (int p = 0; p < 3; p++) {
            JsonNode content = page(p, 2).get("content");
            rows += content.size();
            content.forEach(node -> seenIds.add(node.get("id").asText()));
        }

        assertThat(rows).as("three pages of 2+2+1").isEqualTo(5);
        assertThat(seenIds).as("every row exactly once, none twice, none lost").hasSize(5);
    }

    // P3-1, D.2: search, outcome и диапазон created_at

    @Test
    public void search_matchesActorActionEntityIdAndDetails_caseInsensitively() throws Exception {
        seedFull("TERMINAL", "t-1", "CREATE", "comp-01", "alice@comp1.com", "made a terminal");
        seedFull("COMPANY", "c-1", "UPDATE", "comp-01", "bob@comp1.com", "Renamed to Phoenix");

        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("search", "ALICE")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", Matchers.is(1)))
                .andExpect(jsonPath("$.content[0].entityId", Matchers.is("t-1")));

        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("search", "phoenix")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", Matchers.is(1)))
                .andExpect(jsonPath("$.content[0].entityId", Matchers.is("c-1")));

        // Пустой search значит «без поиска», а не «ничего не нашлось».
        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("search", "   ")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", Matchers.is(2)));
    }

    // «haus» — ловушка: неэкранированный h_u поймал бы и её, поэтому её отсутствие и есть проверка.
    @Test
    public void searchWildcards_percentAndUnderscore_areLiteral() throws Exception {
        seedFull("TERMINAL", "pct", "CREATE", "comp-01", "seeder@test", "Discount 100% applied");
        seedFull("TERMINAL", "und", "CREATE", "comp-01", "seeder@test", "with_underscore");
        seedFull("TERMINAL", "dec", "CREATE", "comp-01", "seeder@test", "haus");

        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("search", "%")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", Matchers.is(1)))
                .andExpect(jsonPath("$.content[0].entityId", Matchers.is("pct")));

        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("search", "h_u")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", Matchers.is(1)))
                .andExpect(jsonPath("$.content[0].entityId", Matchers.is("und")));
    }

    // Скоуп по компании остаётся жёстким предикатом: поиск по тексту чужой записи не найдёт ничего.
    @Test
    public void search_doesNotBypassTheCompanyScope() throws Exception {
        seedFull("TERMINAL", "f-1", "CREATE", "comp-02", "head@comp2.com", "unique foreign words");

        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("search", "unique foreign")
                        .header(HttpHeaders.AUTHORIZATION, headTokenCompany1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", Matchers.is(0)));
    }

    @Test
    public void outcomeFilter_filters_andAnUnknownValueMeansNoFilter() throws Exception {
        seed("TERMINAL", "ok-1", "CREATE", "comp-01");
        auditLogRepository.saveAndFlush(AuditLog.builder()
                .entityType("TERMINAL").entityId("denied-1").action("READ")
                .performedBy("intruder@comp2.com").companyId("comp-01")
                .outcome(az.millikart.common.audit.AuditOutcome.DENIED).build());

        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("outcome", "denied")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", Matchers.is(1)))
                .andExpect(jsonPath("$.content[0].entityId", Matchers.is("denied-1")));

        // Неизвестное enum значение вырождается в «без фильтра»: параметры здесь приводятся, а не
        // отвергаются.
        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("outcome", "WHATEVER")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", Matchers.is(2)));
    }

    @Test
    public void fromAndTo_filterByCreatedAt_andAcceptThePlainDateForm() throws Exception {
        seedAt("old-1", Instant.parse("2026-01-10T12:00:00Z"));
        seedAt("new-1", Instant.parse("2026-08-20T12:00:00Z"));

        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("from", "2026-05-01")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", Matchers.is(1)))
                .andExpect(jsonPath("$.content[0].entityId", Matchers.is("new-1")));

        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("to", "2026-05-01")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", Matchers.is(1)))
                .andExpect(jsonPath("$.content[0].entityId", Matchers.is("old-1")));

        // Голая дата покрывает весь свой UTC-день с обоих концов.
        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("from", "2026-01-10").param("to", "2026-01-10")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", Matchers.is(1)))
                .andExpect(jsonPath("$.content[0].entityId", Matchers.is("old-1")));

        // Мусор приводится к «без фильтра», но никогда к 400 или 500.
        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("from", "not-a-date")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", Matchers.is(2)));
    }

    // Фикстуры

    // Запись с актором и details, по которым ищут тесты поиска; outcome остаётся SUCCESS.
    private void seedFull(String entityType, String entityId, String action, String companyId,
                          String performedBy, String details) {
        auditLogRepository.saveAndFlush(AuditLog.builder()
                .entityType(entityType).entityId(entityId).action(action)
                .performedBy(performedBy).companyId(companyId).details(details).build());
    }

    // Запись с точным created_at вставляется SQL-ом: CreationTimestamp перебивает всё, что задал
    // builder, а тесту D.3 нужны несколько строк на одном моменте, чего через сущность не добиться.
    private void seedAt(String entityId, Instant createdAt) {
        jdbcTemplate.update("""
                INSERT INTO audit_logs (id, entity_type, entity_id, action, performed_by, company_id, outcome, created_at)
                VALUES (?, 'TERMINAL', ?, 'CREATE', 'seeder@test', 'comp-01', 'SUCCESS', ?)
                """,
                java.util.UUID.randomUUID(), entityId, java.sql.Timestamp.from(createdAt));
    }

    private JsonNode page(int page, int size) throws Exception {
        String body = mockMvc.perform(get("/api/v1/audit-logs")
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size))
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private Set<String> contentIds(JsonNode pageNode) {
        Set<String> ids = new HashSet<>();
        pageNode.get("content").forEach(node -> ids.add(node.get("id").asText()));
        return ids;
    }
}
