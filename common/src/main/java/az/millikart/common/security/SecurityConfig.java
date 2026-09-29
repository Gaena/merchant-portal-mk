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

// Запрет по умолчанию (P1-1): любой путь требует токена, если PublicEndpoints не сказал иного; не
// возвращать permitAll(). Авторизация (кому что можно) — в сервисах.
@Configuration
@EnableWebSecurity
// Включено, но не используется: @PreAuthorize нет нигде, роли проверяют сервисы. Не считать вторым
// слоем защиты.
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final TraceIdFilter traceIdFilter;
    private final SecurityErrorResponder securityErrorResponder;

    // Матчеры springdoc — только при включённом флаге: без него springdoc путей не регистрирует.
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
                // CSRF выключен: токен — в заголовке Authorization, не в куке, сессии нет — подделывать нечего.
                .csrf(AbstractHttpConfigurer::disable)
                // CORS выключен намеренно: SPA и API на одном origin через nginx
                // (project_docs/guides/deployment_guide.md); включить — пустить чужие origin'ы.
                .cors(AbstractHttpConfigurer::disable)
                // Без STATELESS Spring Security кладёт исходный запрос каждого отказа в новую
                // HTTP-сессию, и сканирование без токена выделяет по сессии на запрос.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // ERROR-диспатч — внутренний forward на /error: повторная авторизация подменила бы
                    // ошибку пустым 401. Прямой запрос /error — REQUEST-диспатч и требует токена.
                    auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    auth.requestMatchers(PublicEndpoints.PUBLIC_API).permitAll();
                    auth.requestMatchers(PublicEndpoints.INFRASTRUCTURE).permitAll();
                    if (swaggerEnabled) {
                        auth.requestMatchers(PublicEndpoints.SWAGGER).permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                // Отказы отвечают тем же ErrorResponse, что и фильтр.
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
