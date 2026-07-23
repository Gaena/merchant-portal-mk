package az.millikart.auth.service;

import az.millikart.auth.domain.User;
import az.millikart.auth.dto.LoginRequest;
import az.millikart.auth.dto.LoginResponse;
import az.millikart.auth.repository.UserRepository;
import az.millikart.common.exception.BusinessException;
import az.millikart.common.security.JwtProvider;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException("Invalid username or password"));

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException("User account is " + user.getStatus().toLowerCase());
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException("Invalid username or password");
        }

        String token = jwtProvider.generateToken(
                user.getId().toString(),
                user.getUsername(),
                user.getRole(),
                user.getCompanyId()
        );

        return new LoginResponse(token, 86400, user.getRole());
    }
}
