package az.millikart.common.security;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// Запрет по умолчанию: любой путь требует аутентификации, если PublicEndpoints не сказал иного, —
// новый эндпойнт приватен с момента написания. Прежнее anyRequest().permitAll() отдавало всё
// приложение проверке префикса внутри JwtAuthFilter и оставляло открытым всё вне /api/v1/, включая
// actuator и swagger (P1-1). Авторизация (кому что можно) здесь не живёт — она в сервисах.
@Configuration
@EnableWebSecurity
// Включено, но не используется: ни одного @PreAuthorize в коде нет, проверки ролей — явные if в
// сервисах против UserPrincipal.getRole(). Читать как «доступно», а не как второй охраняющий слой,
// и не переносить проверки в аннотации, не убрав их сначала из сервисов.
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final TraceIdFilter traceIdFilter;
    private final SecurityErrorResponder securityErrorResponder;

    // Springdoc регистрирует обработчики только при этом флаге (по умолчанию выключен, включён для
    // приёмочных тестов). Матчеры следуют за флагом: разрешённый матчер несуществующего пути —
    // мёртвая конфигурация, переживающая причину, по которой её добавили.
    private final boolean swaggerEnabled;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          TraceIdFilter traceIdFilter,
                          SecurityErrorResponder securityErrorResponder,
                          @Value("${springdoc.api-docs.enabled:false}") boolean swaggerEnabled) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.traceIdFilter = traceIdFilter;
        this.securityErrorResponder = securityErrorResponder;
        this.swaggerEnabled = swaggerEnabled;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF выключен: аутентификация — bearer-токен из заголовка Authorization, никогда
                // не из куки, так что чужой сайт не заставит браузер его приложить. Сессии, против
                // которой можно подделать запрос, тоже нет.
                .csrf(AbstractHttpConfigurer::disable)
                // CORS выключен намеренно, это не недосмотр: SPA отдаётся с того же origin, что и
                // API, через nginx (deployment_guide.md), кросс-доменных запросов не бывает.
                // Включить CORS — значит начать пускать origin'ы, которым сейчас до API не дойти.
                .cors(AbstractHttpConfigurer::disable)
                // Между запросами не хранится ничего, личность несёт токен. Без этого Spring
                // Security кладёт «исходный запрос» каждого отказа в новую HTTP-сессию, и
                // неаутентифицированное сканирование выделяет по сессии на запрос.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // ERROR-диспатчи — внутренние forward на /error, а не запросы клиента:
                    // повторная авторизация подменила бы настоящую ошибку ничего не значащим 401.
                    // Клиент, запросивший /error напрямую, — REQUEST-диспатч, и токен ему нужен.
                    auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    auth.requestMatchers(PublicEndpoints.PUBLIC_API).permitAll();
                    auth.requestMatchers(PublicEndpoints.INFRASTRUCTURE).permitAll();
                    if (swaggerEnabled) {
                        auth.requestMatchers(PublicEndpoints.SWAGGER).permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                // Оба пути отказа отвечают тем же телом ErrorResponse, что и фильтр.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(securityErrorResponder)
                        .accessDeniedHandler(securityErrorResponder))
                .addFilterBefore(traceIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
