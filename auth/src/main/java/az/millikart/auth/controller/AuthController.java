package az.millikart.auth.controller;

import az.millikart.auth.dto.ChangePasswordRequest;
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

// Весь /api/v1/auth публичен (PublicEndpoints.PUBLIC_API): до входа предъявлять нечего, а refresh
// и logout зовут, когда access-токен потерян или истёк.
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final TrustedProxies trustedProxies;

    public AuthController(AuthService authService, TrustedProxies trustedProxies) {
        this.authService = authService;
        this.trustedProxies = trustedProxies;
    }

    // Адрес — только через ClientIp: по нему считается лимит попыток, и ручной разбор X-Forwarded-For
    // отдал бы ключ лимитера вызывающему.
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, ClientIp.resolve(httpRequest, trustedProxies.addresses()));
    }

    // Обязательная смена пароля, пока сессии нет (Р-100); лимит и локаут — как у входа.
    @PostMapping("/change-password")
    public LoginResponse changePassword(@Valid @RequestBody ChangePasswordRequest request, HttpServletRequest httpRequest) {
        return authService.changePassword(request, ClientIp.resolve(httpRequest, trustedProxies.addresses()));
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    // Всегда 204 (AuthService.logout); нет тела — как неизвестный токен.
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody(required = false) LogoutRequest request) {
        authService.logout(request);
    }
}
