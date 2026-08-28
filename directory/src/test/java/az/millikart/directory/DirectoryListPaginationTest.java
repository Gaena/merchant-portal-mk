package az.millikart.directory;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.millikart.common.security.JwtProvider;
import az.millikart.directory.domain.Company;
import az.millikart.directory.domain.Terminal;
import az.millikart.directory.domain.TerminalStatus;
import az.millikart.directory.repository.CompanyRepository;
import az.millikart.directory.repository.TerminalRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Списки компаний и терминалов после P2-1 и облегчённая выдача терминалов Р-45. Интересный случай
// здесь — две записи с одинаковым именем: именно на них ломается сортировка без уникального
// довеска, и строка оказывается сразу на двух страницах или ни на одной.
@SpringBootTest
@AutoConfigureMockMvc
public class DirectoryListPaginationTest {

    // MAX_PAGE_SIZE из контроллеров — общий потолок для всех постраничных списков.
    private static final int MAX_PAGE_SIZE = 200;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private TerminalRepository terminalRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String auditorToken;
    private String headTokenCompany1;
    private String employeeTokenCompany1;

    @BeforeEach
    public void setup() {
        terminalRepository.deleteAll();
        companyRepository.deleteAll();

        adminToken = "Bearer " + jwtProvider.generateToken("000", "admin@millikart.az", "SYSTEM_ADMIN", null);
        auditorToken = "Bearer " + jwtProvider.generateToken("555", "auditor@millikart.az", "AUDITOR", null);
        headTokenCompany1 = "Bearer " + jwtProvider.generateToken("111", "head@comp1.com", "COMPANY_HEAD", "comp-01");
        employeeTokenCompany1 = "Bearer " + jwtProvider.generateToken("333", "emp@comp1.com", "COMPANY_EMPLOYEE", "comp-01");
    }

    private Company seedCompany(String id, String name, String status) {
        return companyRepository.saveAndFlush(Company.builder()
                .id(id).name(name).status(status).createdBy("seeder").updatedBy("seeder").build());
    }

    private Terminal seedTerminal(int id, String name, String companyId, TerminalStatus status) {
        return terminalRepository.saveAndFlush(Terminal.builder()
                .id(id).name(name)
                .login("term_login_" + id)
                .password("term_pass_" + id)
                .companyId(companyId).status(status)
                .createdBy("seeder").updatedBy("seeder").build());
    }

    private JsonNode body(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private List<String> idsOnPage(String path, String token, int page, int size) throws Exception {
        JsonNode json = body(mockMvc.perform(get(path)
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size))
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()));
        List<String> ids = new ArrayList<>();
        json.get("content").forEach(node -> ids.add(node.get("id").asText()));
        return ids;
    }

    // 3. Соседние страницы не пересекаются, а totalElements не врёт

    @Test
    public void companyPages_doNotOverlap_andTotalIsCorrect() throws Exception {
        for (int i = 0; i < 7; i++) {
            seedCompany("comp-" + i, "Company " + i, "ACTIVE");
        }

        mockMvc.perform(get("/api/v1/companies")
                        .param("page", "0").param("size", "3")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.totalElements", is(7)))
                .andExpect(jsonPath("$.totalPages", is(3)))
                .andExpect(jsonPath("$.number", is(0)));

        Set<String> seen = new HashSet<>();
        seen.addAll(idsOnPage("/api/v1/companies", adminToken, 0, 3));
        seen.addAll(idsOnPage("/api/v1/companies", adminToken, 1, 3));
        seen.addAll(idsOnPage("/api/v1/companies", adminToken, 2, 3));
        Assertions.assertEquals(7, seen.size(), "every company seen exactly once across the pages");
    }

    @Test
    public void terminalPages_doNotOverlap_andTotalIsCorrect() throws Exception {
        seedCompany("comp-01", "MilliKart LLC", "ACTIVE");
        for (int i = 0; i < 5; i++) {
            seedTerminal(500100 + i, "Terminal " + i, "comp-01", TerminalStatus.ACTIVE);
        }

        mockMvc.perform(get("/api/v1/terminals")
                        .param("page", "0").param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", is(5)))
                .andExpect(jsonPath("$.totalPages", is(3)));

        Set<String> seen = new HashSet<>();
        seen.addAll(idsOnPage("/api/v1/terminals", adminToken, 0, 2));
        seen.addAll(idsOnPage("/api/v1/terminals", adminToken, 1, 2));
        seen.addAll(idsOnPage("/api/v1/terminals", adminToken, 2, 2));
        Assertions.assertEquals(5, seen.size(), "every terminal seen exactly once across the pages");
    }

    // 4. Одинаковые имена не должны ни дублироваться между страницами, ни исчезать

    // Две компании с одинаковым именем при size=1: при сортировке только по name БД вправе
    // упорядочить пару по-своему на каждый запрос страницы, и одна вернётся дважды, а вторая ни
    // разу. Довесок id делает порядок полным, поэтому каждая обязана прийти ровно по разу.
    @Test
    public void companiesWithTheSameName_appearExactlyOnce_acrossPages() throws Exception {
        seedCompany("comp-a", "Duplicate Name LLC", "ACTIVE");
        seedCompany("comp-b", "Duplicate Name LLC", "ACTIVE");
        seedCompany("comp-c", "Another LLC", "ACTIVE");

        List<String> collected = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            collected.addAll(idsOnPage("/api/v1/companies", adminToken, page, 1));
        }

        Assertions.assertEquals(3, collected.size(), "one row per page, three pages");
        Assertions.assertEquals(3, new HashSet<>(collected).size(),
                "no company may be returned twice: " + collected);
        Assertions.assertTrue(collected.containsAll(List.of("comp-a", "comp-b", "comp-c")),
                "no company may be skipped: " + collected);
    }

    @Test
    public void terminalsWithTheSameName_appearExactlyOnce_acrossPages() throws Exception {
        seedCompany("comp-01", "MilliKart LLC", "ACTIVE");
        seedTerminal(500201, "Same Name", "comp-01", TerminalStatus.ACTIVE);
        seedTerminal(500202, "Same Name", "comp-01", TerminalStatus.ACTIVE);

        List<String> collected = new ArrayList<>();
        collected.addAll(idsOnPage("/api/v1/terminals", adminToken, 0, 1));
        collected.addAll(idsOnPage("/api/v1/terminals", adminToken, 1, 1));

        Assertions.assertEquals(2, new HashSet<>(collected).size(),
                "no terminal may be returned twice or skipped: " + collected);
    }

    // 5. Значения page/size вне диапазона приводятся, а не отвергаются

    @Test
    public void sizeAboveCeiling_isClampedToCeiling() throws Exception {
        seedCompany("comp-01", "MilliKart LLC", "ACTIVE");
        seedTerminal(500301, "Terminal", "comp-01", TerminalStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/companies")
                        .param("size", "5000")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", is(MAX_PAGE_SIZE)));

        mockMvc.perform(get("/api/v1/terminals")
                        .param("size", "5000")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", is(MAX_PAGE_SIZE)));
    }

    @Test
    public void negativePageAndSize_areCoerced_notRejected() throws Exception {
        seedCompany("comp-01", "MilliKart LLC", "ACTIVE");
        seedTerminal(500401, "Terminal", "comp-01", TerminalStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/companies")
                        .param("page", "-4").param("size", "-1")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number", is(0)))
                .andExpect(jsonPath("$.size", is(1)));

        mockMvc.perform(get("/api/v1/terminals")
                        .param("page", "-4").param("size", "0")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number", is(0)))
                .andExpect(jsonPath("$.size", is(1)));
    }

    // Мягко удалённые компании не попадают ни на страницу, ни в счётчик

    @Test
    public void deletedCompanies_areAbsentFromContentAndTotal() throws Exception {
        seedCompany("comp-alive", "Alive LLC", "ACTIVE");
        seedCompany("comp-gone", "Gone LLC", "DELETED");

        mockMvc.perform(get("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].id", is("comp-alive")));
    }

    // 6. Правила ролей не сдвинулись

    @Test
    public void companyHead_isRefusedTheCompanyList_asBefore() throws Exception {
        seedCompany("comp-01", "MilliKart LLC", "ACTIVE");

        mockMvc.perform(get("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, headTokenCompany1))
                .andExpect(status().isForbidden());
    }

    @Test
    public void auditor_stillReadsTheCompanyList() throws Exception {
        seedCompany("comp-01", "MilliKart LLC", "ACTIVE");

        mockMvc.perform(get("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    public void companyHead_seesOnlyOwnCompanyTerminals_acrossPages() throws Exception {
        seedCompany("comp-01", "MilliKart LLC", "ACTIVE");
        seedCompany("comp-02", "Other LLC", "ACTIVE");
        seedTerminal(500501, "Own A", "comp-01", TerminalStatus.ACTIVE);
        seedTerminal(500502, "Own B", "comp-01", TerminalStatus.ACTIVE);
        seedTerminal(500503, "Foreign A", "comp-02", TerminalStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/terminals")
                        .header(HttpHeaders.AUTHORIZATION, headTokenCompany1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(2)));

        List<String> collected = new ArrayList<>();
        collected.addAll(idsOnPage("/api/v1/terminals", headTokenCompany1, 0, 1));
        collected.addAll(idsOnPage("/api/v1/terminals", headTokenCompany1, 1, 1));
        Assertions.assertEquals(Set.of("500501", "500502"), new HashSet<>(collected),
                "a COMPANY_HEAD must not reach another company's terminals on any page");
    }

    // P3-1: поиск серверный, поэтому видит дальше текущей страницы

    // Запись лежит за первой страницей, но поиск обязан вернуть её на странице 0.
    @Test
    public void companySearch_findsARecordThatIsNotOnTheFirstPage() throws Exception {
        for (int i = 0; i < 25; i++) {
            seedCompany(String.format("srch-%02d", i), String.format("Firm %02d", i), "ACTIVE");
        }

        mockMvc.perform(get("/api/v1/companies")
                        .param("size", "10").param("search", "Firm 22")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.totalPages", is(1)))
                .andExpect(jsonPath("$.content[0].id", is("srch-22")));
    }

    // Регистр не важен, а пустой search значит «без поиска», а не «ничего не нашлось».
    @Test
    public void companySearch_isCaseInsensitive_andBlankMeansNoFilter() throws Exception {
        seedCompany("comp-a", "MilliKart LLC", "ACTIVE");
        seedCompany("comp-b", "Other LLC", "ACTIVE");

        mockMvc.perform(get("/api/v1/companies")
                        .param("search", "millikart")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].id", is("comp-a")));

        mockMvc.perform(get("/api/v1/companies")
                        .param("search", "   ")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(2)));
    }

    // % не должен возвращать всю таблицу, а _ не должен работать как «любой символ»: «Parts» —
    // ловушка, неэкранированный r_s поймал бы и её, поэтому её отсутствие и есть проверка.
    @Test
    public void companySearch_percentAndUnderscore_areLiteral() throws Exception {
        seedCompany("comp-pct", "Discount 100% LLC", "ACTIVE");
        seedCompany("comp-und", "under_score LLC", "ACTIVE");
        seedCompany("comp-parts", "Parts LLC", "ACTIVE");

        mockMvc.perform(get("/api/v1/companies")
                        .param("search", "%")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].id", is("comp-pct")));

        mockMvc.perform(get("/api/v1/companies")
                        .param("search", "r_s")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].id", is("comp-und")));
    }

    @Test
    public void terminalSearch_findsARecordThatIsNotOnTheFirstPage_alsoByIdAsText() throws Exception {
        seedCompany("comp-01", "MilliKart LLC", "ACTIVE");
        for (int i = 0; i < 25; i++) {
            seedTerminal(700000 + i, String.format("Point %02d", i), "comp-01", TerminalStatus.ACTIVE);
        }

        mockMvc.perform(get("/api/v1/terminals")
                        .param("size", "10").param("search", "point 22")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].id", is(700022)));

        // Id ищется как текст — именно так терминалы называют друг другу вслух.
        mockMvc.perform(get("/api/v1/terminals")
                        .param("size", "10").param("search", "700022")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].id", is(700022)));
    }

    // Имя компании — тоже поле поиска в списке терминалов; смысл в join: в самой строке терминала
    // слова «Other» нет.
    @Test
    public void terminalSearch_findsATerminalByCompanyNameAlone() throws Exception {
        seedCompany("comp-01", "MilliKart LLC", "ACTIVE");
        seedCompany("comp-02", "Other LLC", "ACTIVE");
        seedTerminal(700101, "Alpha", "comp-01", TerminalStatus.ACTIVE);
        seedTerminal(700102, "Beta", "comp-02", TerminalStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/terminals")
                        .param("search", "Other")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].id", is(700102)));
    }

    // Скоуп по компании — условие запроса, и поиск не имеет права его расширять.
    @Test
    public void terminalSearch_doesNotBypassTheCompanyScope() throws Exception {
        seedCompany("comp-01", "MilliKart LLC", "ACTIVE");
        seedCompany("comp-02", "Other LLC", "ACTIVE");
        seedTerminal(700201, "Own Point", "comp-01", TerminalStatus.ACTIVE);
        seedTerminal(700202, "Unique Foreign Point", "comp-02", TerminalStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/terminals")
                        .param("search", "Unique Foreign")
                        .header(HttpHeaders.AUTHORIZATION, headTokenCompany1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    // То же правило буквальных wildcard на терминалах; «haus» — ловушка для неэкранированного h_u.
    @Test
    public void terminalSearch_percentAndUnderscore_areLiteral() throws Exception {
        seedCompany("comp-01", "MilliKart LLC", "ACTIVE");
        terminalRepository.saveAndFlush(Terminal.builder()
                .id(700301).name("Rate 100%").login("loginA").password("x")
                .companyId("comp-01").status(TerminalStatus.ACTIVE)
                .createdBy("seeder").updatedBy("seeder").build());
        terminalRepository.saveAndFlush(Terminal.builder()
                .id(700302).name("with_underscore").login("loginB").password("x")
                .companyId("comp-01").status(TerminalStatus.ACTIVE)
                .createdBy("seeder").updatedBy("seeder").build());
        terminalRepository.saveAndFlush(Terminal.builder()
                .id(700303).name("haus").login("loginC").password("x")
                .companyId("comp-01").status(TerminalStatus.ACTIVE)
                .createdBy("seeder").updatedBy("seeder").build());

        mockMvc.perform(get("/api/v1/terminals")
                        .param("search", "%")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].id", is(700301)));

        mockMvc.perform(get("/api/v1/terminals")
                        .param("search", "h_u")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].id", is(700302)));
    }

    // 7-9. Облегчённая выдача: /api/v1/terminals/options

    // Заблокированные терминалы обязаны быть в ответе (Р-45): экран транзакций разрешает по этому
    // списку имя терминала старого платежа, и снятый с обслуживания терминал должен сохранить имя
    // в тех строках. Фильтрует форма ссылки, а не сервер.
    @Test
    public void options_includeBlockedTerminals() throws Exception {
        seedCompany("comp-01", "MilliKart LLC", "ACTIVE");
        seedTerminal(500601, "Active Terminal", "comp-01", TerminalStatus.ACTIVE);
        seedTerminal(500602, "Blocked Terminal", "comp-01", TerminalStatus.BLOCKED);

        JsonNode json = body(mockMvc.perform(get("/api/v1/terminals/options")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2))));

        Set<String> statuses = new HashSet<>();
        json.forEach(node -> statuses.add(node.get("status").asText()));
        Assertions.assertEquals(Set.of("ACTIVE", "BLOCKED"), statuses,
                "a blocked terminal must still be offered to the caller: " + json);
    }

    // Эквайринговых кредов в TerminalOptionResponse нет вовсе. Проверка идёт по сырому телу, а не
    // по разобранным полям: так поймается и поле, добавленное позже неаккуратным маппером.
    @Test
    public void options_carryNoTerminalCredentials() throws Exception {
        seedCompany("comp-01", "MilliKart LLC", "ACTIVE");
        seedTerminal(500701, "Terminal", "comp-01", TerminalStatus.ACTIVE);

        String raw = mockMvc.perform(get("/api/v1/terminals/options")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Assertions.assertFalse(raw.contains("login"), "the feed must not name a login field: " + raw);
        Assertions.assertFalse(raw.contains("password"), "the feed must not name a password field: " + raw);
        Assertions.assertFalse(raw.contains("term_login_500701"), "no credential value may leak: " + raw);
        Assertions.assertFalse(raw.contains("term_pass_500701"), "no credential value may leak: " + raw);

        JsonNode json = objectMapper.readTree(raw);
        Assertions.assertEquals(List.of("id", "name", "status"), fieldNames(json.get(0)),
                "three fields, nothing else");
    }

    @Test
    public void options_forCompanyHead_areLimitedToOwnCompany() throws Exception {
        seedCompany("comp-01", "MilliKart LLC", "ACTIVE");
        seedCompany("comp-02", "Other LLC", "ACTIVE");
        seedTerminal(500801, "Own Terminal", "comp-01", TerminalStatus.ACTIVE);
        seedTerminal(500802, "Own Blocked", "comp-01", TerminalStatus.BLOCKED);
        seedTerminal(500803, "Foreign Terminal", "comp-02", TerminalStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/terminals/options")
                        .header(HttpHeaders.AUTHORIZATION, headTokenCompany1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is(500802)))
                .andExpect(jsonPath("$[1].id", is(500801)));
    }

    // COMPANY_EMPLOYEE читает терминалы: на этом селекторе строится форма создания ссылки.
    @Test
    public void options_forCompanyEmployee_areAllowed_asTheFullListIs() throws Exception {
        seedCompany("comp-01", "MilliKart LLC", "ACTIVE");
        seedTerminal(500901, "Own Terminal", "comp-01", TerminalStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/terminals/options")
                        .header(HttpHeaders.AUTHORIZATION, employeeTokenCompany1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(500901)));
    }

    private List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
