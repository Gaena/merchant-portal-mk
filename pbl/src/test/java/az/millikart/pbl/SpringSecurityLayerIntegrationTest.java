package az.millikart.pbl;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.millikart.common.security.JwtAuthFilter;
import az.millikart.common.security.JwtProvider;
import az.millikart.common.security.SecurityErrorResponder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

// Закрепляет половину P1-1, приходящуюся на Spring Security, отдельно. По умолчанию отказывают два
// независимых слоя: JwtAuthFilter и anyRequest().authenticated() в SecurityConfig. Избыточность
// намеренная, но обычные тесты не отличают их друг от друга. Поэтому фильтр здесь подменён на
// пропускающий всех: отвечает только Spring Security, и без его правила класс краснеет.
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@Import(SpringSecurityLayerIntegrationTest.PassThroughFilterConfig.class)
class SpringSecurityLayerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    // Доказательство, что подмена сработала: с настоящим фильтром валидный токен был бы принят и
    // здесь было бы 200 или 403, но не 401. Если тест упал — бин не переопределён, и всё остальное
    // в классе меряет не то.
    @Test
    void tokenFilterIsNeutralised_soEvenAValidTokenAuthenticatesNobody() throws Exception {
        String token = "Bearer " + jwtProvider.generateToken(
                "layer-admin", "layer-admin@test.com", "SYSTEM_ADMIN", null);

        mockMvc.perform(get("/api/v1/payment-links").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    // Регрессионный сторож SecurityConfig: без него здесь вернётся 404, а не 401.
    @Test
    void unknownPath_isRejectedByspringSecurityAlone() throws Exception {
        mockMvc.perform(get("/some/unmapped/path"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/some/unmapped/path"));
    }

    // Тот же сторож для путей, которые старая проверка префикса пропускала по построению.
    @Test
    void springdocPaths_areRejectedBySpringSecurityAlone() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized());
    }

    // А разрешённые пути разрешает сам Spring Security, а не только фильтр.
    @Test
    void publicPaths_arePermittedBySpringSecurityAlone() throws Exception {
        // Доходит до контроллера и получает ответ по существу, а не отказ из-за отсутствия токена.
        mockMvc.perform(get("/api/v1/payment-links/redirect/{tx}", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isOk());

        // Разрешено матчером actuator-путей; на основном порту там ничего не замаплено.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isNotFound());
    }

    @TestConfiguration
    static class PassThroughFilterConfig {

        // Имя бина совпадает с Component, поэтому он заменяет его, а не добавляется: два фильтра
        // работали бы оба, и настоящий продолжал бы сторожить.
        @Bean("jwtAuthFilter")
        JwtAuthFilter jwtAuthFilter(JwtProvider jwtProvider, SecurityErrorResponder responder) {
            return new JwtAuthFilter(jwtProvider, responder, "", false, false) {
                @Override
                protected void doFilterInternal(HttpServletRequest request,
                                                HttpServletResponse response,
                                                FilterChain filterChain)
                        throws ServletException, IOException {
                    filterChain.doFilter(request, response);
                }
            };
        }
    }
}
