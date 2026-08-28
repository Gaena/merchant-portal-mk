package az.millikart.auth;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.millikart.auth.domain.Company;
import az.millikart.auth.domain.User;
import az.millikart.auth.repository.CompanyRepository;
import az.millikart.auth.repository.UserRepository;
import az.millikart.common.security.JwtProvider;
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

// Список аккаунтов после P2-1: страницы приходят из базы, порядок стабилен между запросами
// страниц, а ролевые правила — ровно те же, что были у эндпоинта с голым списком.
@SpringBootTest
@AutoConfigureMockMvc
public class UserListPaginationTest {

    // UserController.MAX_PAGE_SIZE — потолок, общий для всех страничных списков.
    private static final int MAX_PAGE_SIZE = 200;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String headTokenCompany1;
    private String employeeToken;

    @BeforeEach
    public void setup() {
        userRepository.deleteAll();
        companyRepository.deleteAll();
        companyRepository.save(Company.builder().id("comp-01").name("MilliKart LLC").status("ACTIVE").build());
        companyRepository.save(Company.builder().id("comp-02").name("Other LLC").status("ACTIVE").build());

        adminToken = "Bearer " + jwtProvider.generateToken("000", "admin@millikart.az", "SYSTEM_ADMIN", null);
        headTokenCompany1 = "Bearer " + jwtProvider.generateToken("111", "head@comp1.com", "COMPANY_HEAD", "comp-01");
        employeeToken = "Bearer " + jwtProvider.generateToken("333", "emp@comp1.com", "COMPANY_EMPLOYEE", "comp-01");
    }

    private User seed(String username, String companyId, String status) {
        return userRepository.saveAndFlush(User.builder()
                .username(username)
                .passwordHash("not-a-real-hash")
                .fullName("Seeded " + username)
                .role("COMPANY_EMPLOYEE")
                .companyId(companyId)
                .status(status)
                .build());
    }

    private JsonNode body(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private List<String> usernamesOnPage(String token, int page, int size) throws Exception {
        JsonNode json = body(mockMvc.perform(get("/api/v1/users")
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size))
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()));
        List<String> usernames = new ArrayList<>();
        json.get("content").forEach(node -> usernames.add(node.get("username").asText()));
        return usernames;
    }

    // 3. Соседние страницы не пересекаются, а totalElements говорит правду.

    @Test
    public void pages_doNotOverlap_andTotalIsCorrect() throws Exception {
        for (int i = 0; i < 7; i++) {
            seed("user" + i + "@comp1.com", "comp-01", "ACTIVE");
        }

        mockMvc.perform(get("/api/v1/users")
                        .param("page", "0").param("size", "3")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.totalElements", is(7)))
                .andExpect(jsonPath("$.totalPages", is(3)))
                .andExpect(jsonPath("$.number", is(0)))
                .andExpect(jsonPath("$.size", is(3)));

        List<String> first = usernamesOnPage(adminToken, 0, 3);
        List<String> second = usernamesOnPage(adminToken, 1, 3);
        List<String> third = usernamesOnPage(adminToken, 2, 3);

        Set<String> seen = new HashSet<>(first);
        seen.addAll(second);
        seen.addAll(third);
        Assertions.assertEquals(7, seen.size(), "every account seen exactly once across the pages");
        Assertions.assertEquals(1, third.size(), "the last page carries the remainder");
    }

    // 5. Значения page/size вне диапазона поджимаются, а не отвергаются.

    @Test
    public void sizeAboveCeiling_isClampedToCeiling() throws Exception {
        seed("only@comp1.com", "comp-01", "ACTIVE");

        mockMvc.perform(get("/api/v1/users")
                        .param("size", "5000")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", is(MAX_PAGE_SIZE)));
    }

    @Test
    public void negativePageAndSize_areCoerced_notRejected() throws Exception {
        seed("only@comp1.com", "comp-01", "ACTIVE");

        mockMvc.perform(get("/api/v1/users")
                        .param("page", "-4").param("size", "-1")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number", is(0)))
                .andExpect(jsonPath("$.size", is(1)));
    }

    // Мягко удалённые аккаунты не попадают ни в страницу, ни в счётчик.

    // Раньше фильтр работал в Java после чтения всей таблицы. Теперь он часть запроса — именно
    // поэтому totalElements совпадает с тем, что вызывающий реально может пролистать.
    @Test
    public void deletedAccounts_areAbsentFromContentAndTotal() throws Exception {
        seed("alive@comp1.com", "comp-01", "ACTIVE");
        seed("gone@comp1.com", "comp-01", "DELETED");

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].username", is("alive@comp1.com")));
    }

    // 6. Ролевые правила не сдвинулись.

    @Test
    public void companyHead_seesOnlyOwnCompany_acrossPages() throws Exception {
        for (int i = 0; i < 4; i++) {
            seed("own" + i + "@comp1.com", "comp-01", "ACTIVE");
        }
        for (int i = 0; i < 4; i++) {
            seed("foreign" + i + "@comp2.com", "comp-02", "ACTIVE");
        }

        mockMvc.perform(get("/api/v1/users")
                        .param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, headTokenCompany1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(4)))
                .andExpect(jsonPath("$.content[0].companyId", is("comp-01")))
                .andExpect(jsonPath("$.content[3].companyId", is("comp-01")));

        // При мелкой странице чужие аккаунты тоже не должны всплыть ни на одной из них.
        List<String> all = new ArrayList<>();
        all.addAll(usernamesOnPage(headTokenCompany1, 0, 2));
        all.addAll(usernamesOnPage(headTokenCompany1, 1, 2));
        all.addAll(usernamesOnPage(headTokenCompany1, 2, 2));
        Assertions.assertEquals(4, all.size());
        Assertions.assertTrue(all.stream().noneMatch(u -> u.contains("comp2")),
                "a COMPANY_HEAD must not see another company's accounts on any page");
    }

    @Test
    public void companyEmployee_isRefused_asBefore() throws Exception {
        seed("own@comp1.com", "comp-01", "ACTIVE");

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, employeeToken))
                .andExpect(status().isForbidden());
    }

    // P3-1: поиск серверный, поэтому видит дальше текущей страницы.

    private ResultActions search(String token, String search) throws Exception {
        return mockMvc.perform(get("/api/v1/users")
                        .param("size", "10")
                        .param("search", search)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
    }

    // Ради чего фича и появилась: запись лежит далеко за первой страницей, и поиск — в отличие
    // от клиентского фильтра, который он заменил, — обязан найти её на странице 0.
    @Test
    public void search_findsARecordThatIsNotOnTheFirstPage() throws Exception {
        for (int i = 0; i < 25; i++) {
            seed(String.format("acct%02d@comp1.com", i), "comp-01", "ACTIVE");
        }

        search(adminToken, "acct22")
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.number", is(0)))
                .andExpect(jsonPath("$.content[0].username", is("acct22@comp1.com")));
    }

    @Test
    public void search_isCaseInsensitive() throws Exception {
        seed("acct22@comp1.com", "comp-01", "ACTIVE");

        search(adminToken, "ACCT22")
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].username", is("acct22@comp1.com")));
    }

    // Пустой и состоящий из пробелов поиск значат "поиска нет": полный список, а не пустой.
    @Test
    public void blankSearch_isTheSameAsNoSearch() throws Exception {
        seed("one@comp1.com", "comp-01", "ACTIVE");
        seed("two@comp1.com", "comp-01", "ACTIVE");

        search(adminToken, "").andExpect(jsonPath("$.totalElements", is(2)));
        search(adminToken, "   ").andExpect(jsonPath("$.totalElements", is(2)));
    }

    // Самая вероятная дыра: введённые пользователем % и _ — подстановочные знаки LIKE, если их
    // не экранировать. % не должен вернуть всю таблицу, а _ не должен значить "любой один
    // символ" — оба ищутся буквально.
    @Test
    public void percentAndUnderscore_areSearchedLiterally() throws Exception {
        userRepository.saveAndFlush(User.builder()
                .username("discount@comp1.com").passwordHash("x").fullName("100% off")
                .role("COMPANY_EMPLOYEE").companyId("comp-01").status("ACTIVE").build());
        userRepository.saveAndFlush(User.builder()
                .username("snake@comp1.com").passwordHash("x").fullName("under_score")
                .role("COMPANY_EMPLOYEE").companyId("comp-01").status("ACTIVE").build());
        seed("plain@comp1.com", "comp-01", "ACTIVE");

        search(adminToken, "%")
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].username", is("discount@comp1.com")));
        search(adminToken, "r_s")
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].username", is("snake@comp1.com")));
    }

    // P3-1a: страж для COMPANY_HEAD без компании. Пустой ответ значит "запрос не выполнялся", а
    // не "ничего не нашлось": в нативном запросе P3-1 :companyId IS NULL значит "фильтра нет" —
    // все; без явной ветки с пустой страницей в listUsers этот вызывающий увидел бы всю таблицу.
    // Потому это не дубль search_doesNotBypassTheCompanyScope (там компания есть). 200, не 403.
    @Test
    public void companyHeadWithoutCompany_seesNobody_notEveryone() throws Exception {
        String headWithoutCompany = "Bearer " + jwtProvider.generateToken(
                "222", "head-nocompany@millikart.az", "COMPANY_HEAD", null);
        seed("one@comp1.com", "comp-01", "ACTIVE");
        seed("two@comp2.com", "comp-02", "ACTIVE");
        // Аккаунт вовсе без компании: доказывает, что пустая страница — не совпадение NULL с NULL.
        seed("orphan@nowhere.com", null, "ACTIVE");

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, headWithoutCompany))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)));

        // Два пути, добавленных P3-1, не должны стать обходом того же стража.
        mockMvc.perform(get("/api/v1/users")
                        .param("search", "one@comp1")
                        .header(HttpHeaders.AUTHORIZATION, headWithoutCompany))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)));

        mockMvc.perform(get("/api/v1/users")
                        .param("role", "COMPANY_EMPLOYEE")
                        .header(HttpHeaders.AUTHORIZATION, headWithoutCompany))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    // Скоуп компании — условие запроса, и поиск не должен его расширять.
    @Test
    public void search_doesNotBypassTheCompanyScope() throws Exception {
        seed("own@comp1.com", "comp-01", "ACTIVE");
        seed("target@comp2.com", "comp-02", "ACTIVE");

        search(headTokenCompany1, "target")
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    // Десять страниц на три попадания — это когда счётчик игнорирует фильтр.
    @Test
    public void totalElements_countsTheFilteredSet_notTheTable() throws Exception {
        for (int i = 0; i < 20; i++) {
            seed("noise" + i + "@comp1.com", "comp-01", "ACTIVE");
        }
        for (int i = 0; i < 3; i++) {
            seed("needle" + i + "@comp1.com", "comp-01", "ACTIVE");
        }

        search(adminToken, "needle")
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.totalPages", is(1)));
    }

    // Единственное поле из таблицы другого модуля: совпадение идёт по НАЗВАНИЮ компании, которое
    // есть только в companies (схема directory) — в строке пользователя его нет.
    @Test
    public void search_findsAUserByCompanyNameAlone() throws Exception {
        seed("bob@comp2.com", "comp-02", "ACTIVE");
        seed("alice@comp1.com", "comp-01", "ACTIVE");

        // "Other LLC" — название comp-02; ни логин, ни ФИО, ни company id не содержат "other".
        search(adminToken, "Other")
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].username", is("bob@comp2.com")));
    }

    // P3-1: фильтр по роли тоже серверный.

    // На клиенте он был бы хуже бесполезного: серверный поиск находит аккаунт на любой странице,
    // а клиентский фильтр по роли снова спрятал бы его, окажись страница не текущей.
    @Test
    public void roleFilter_filtersOnTheServer_andIsCaseInsensitive() throws Exception {
        seed("emp@comp1.com", "comp-01", "ACTIVE");
        userRepository.saveAndFlush(User.builder()
                .username("auditor@comp1.com").passwordHash("x").fullName("The Auditor")
                .role("AUDITOR").companyId("comp-01").status("ACTIVE").build());

        mockMvc.perform(get("/api/v1/users")
                        .param("role", "auditor")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].username", is("auditor@comp1.com")));
    }

    // Неизвестная роль вырождается в "фильтра нет": устаревший список не должен давать 400 или 500.
    @Test
    public void unknownRole_meansNoRoleFilter() throws Exception {
        seed("one@comp1.com", "comp-01", "ACTIVE");
        seed("two@comp1.com", "comp-01", "ACTIVE");

        mockMvc.perform(get("/api/v1/users")
                        .param("role", "SUPERHERO")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(2)));
    }
}
