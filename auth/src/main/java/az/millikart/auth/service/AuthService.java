package az.millikart.auth.service;

import az.millikart.auth.domain.User;
import az.millikart.auth.dto.LoginRequest;
import az.millikart.auth.dto.LoginResponse;
import az.millikart.auth.repository.UserRepository;
import az.millikart.common.exception.BusinessException;
import az.millikart.common.security.JwtProvider;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse login(LoginRequest request) {
        String cleanEmail = request.username() != null ? request.username().trim().toLowerCase() : "";
        log.info("Login attempt for email: {}", cleanEmail);

        User user = userRepository.findByUsername(cleanEmail)
                .orElseThrow(() -> {
                    log.warn("Login failed: username {} not found", cleanEmail);
                    return new BusinessException("Invalid username or password");
                });

        if (!"ACTIVE".equals(user.getStatus())) {
            log.warn("Login failed: user account {} is in status {}", request.username(), user.getStatus());
            throw new BusinessException("User account is " + user.getStatus().toLowerCase());
        }

        // PCI-DSS 8.3.4: Check account lockout status
        if (user.getLockoutUntil() != null) {
            if (user.getLockoutUntil().isAfter(Instant.now())) {
                log.warn("Login blocked: user account {} is locked until {}", request.username(), user.getLockoutUntil());
                throw new BusinessException("Account is locked due to multiple failed login attempts. Please try again later.");
            } else {
                // Lockout period expired, reset counter
                log.info("Account lockout expired for username {}. Resetting lockout state.", request.username());
                user.setLockoutUntil(null);
                user.setFailedLoginAttempts(0);
            }
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int attempts = (user.getFailedLoginAttempts() != null ? user.getFailedLoginAttempts() : 0) + 1;
            user.setFailedLoginAttempts(attempts);

            if (attempts >= 6) {
                user.setLockoutUntil(Instant.now().plus(30, ChronoUnit.MINUTES));
                log.warn("Account {} locked for 30 minutes due to 6 failed login attempts (PCI-DSS 8.3.4)", request.username());
            } else {
                log.warn("Login failed: incorrect password for username {}. Failed attempts: {}/6", request.username(), attempts);
            }

            userRepository.save(user);
            throw new BusinessException("Invalid username or password");
        }

        // Reset failed login attempts on successful login
        if ((user.getFailedLoginAttempts() != null && user.getFailedLoginAttempts() > 0) || user.getLockoutUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockoutUntil(null);
            userRepository.save(user);
        }

        String token = jwtProvider.generateToken(
                user.getId().toString(),
                user.getUsername(),
                user.getRole(),
                user.getCompanyId()
        );

        log.info("Login successful for user ID: {}, role: {}, companyId: {}", user.getId(), user.getRole(), user.getCompanyId());
        return new LoginResponse(token, 86400, user.getRole());
    }
}
