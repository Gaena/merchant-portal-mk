package az.millikart.auth.controller;

import az.millikart.auth.dto.LoginRequest;
import az.millikart.auth.dto.LoginResponse;
import az.millikart.auth.dto.LogoutRequest;
import az.millikart.auth.dto.RefreshRequest;
import az.millikart.auth.service.AuthService;
import az.millikart.common.web.ClientIp;
import az.millikart.common.web.TrustedProxies;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Login / refresh / logout. Все три публичны через PublicEndpoints.PUBLIC_API (весь /api/v1/auth):
// до входа предъявлять нечего, а refresh и logout клиент зовёт ровно тогда, когда его access-токен
// потерян или истёк.
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final TrustedProxies trustedProxies;

    public AuthController(AuthService authService, TrustedProxies trustedProxies) {
        this.authService = authService;
        this.trustedProxies = trustedProxies;
    }

    // Адрес клиента резолвится здесь, один раз, через ClientIp — сервис получает строку и заголовка
    // не видит. По этому адресу считается лимит попыток, поэтому разбор X-Forwarded-For руками где
    // угодно отдал бы ключ лимитера самому вызывающему.
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, ClientIp.resolve(httpRequest, trustedProxies.addresses()));
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    // Всегда 204 — см. AuthService.logout. Отсутствующее тело равнозначно неизвестному токену.
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody(required = false) LogoutRequest request) {
        authService.logout(request);
    }
}
