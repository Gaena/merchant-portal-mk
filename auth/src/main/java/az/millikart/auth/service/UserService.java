package az.millikart.auth.service;

import az.millikart.auth.domain.User;
import az.millikart.auth.dto.CreateUserRequest;
import az.millikart.auth.dto.UpdateUserRequest;
import az.millikart.auth.dto.UserResponse;
import az.millikart.auth.repository.CompanyRepository;
import az.millikart.auth.repository.UserRepository;
import az.millikart.common.exception.BusinessException;
import az.millikart.common.exception.InvalidStateException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millikart.common.security.UserPrincipal;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, CompanyRepository companyRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request, UserPrincipal principal) {
        String actorRole = UserPrincipal.getRole(principal);
        String actorCompanyId = UserPrincipal.getCompanyId(principal);
        String actorUsername = UserPrincipal.getUsername(principal);

        String cleanEmail = request.username() != null ? request.username().trim().toLowerCase() : "";

        log.info("Request to create user: username={}, role={}, companyId={} by actor: {}", 
                cleanEmail, request.role(), request.companyId(), actorUsername);

        // Enforce RBAC
        validateCreatePermission(request, actorRole, actorCompanyId);

        // Check uniqueness
        if (userRepository.findByUsername(cleanEmail).isPresent()) {
            log.warn("User creation failed: username {} already exists", cleanEmail);
            throw new BusinessException("Username already exists");
        }

        // Validate Company exists if assigned
        if (request.companyId() != null && !request.companyId().isBlank()) {
            if (!companyRepository.existsById(request.companyId())) {
                log.warn("User creation failed: companyId {} not found", request.companyId());
                throw new BusinessException("Company not found");
            }
        }

        User user = User.builder()
                .username(cleanEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(request.role())
                .companyId(request.companyId())
                .status("ACTIVE")
                .build();

        user = userRepository.save(user);
        log.info("User created successfully: id={}, username={}", user.getId(), user.getUsername());
        return mapToResponse(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(UserPrincipal principal) {
        String actorRole = UserPrincipal.getRole(principal);
        String actorCompanyId = UserPrincipal.getCompanyId(principal);
        List<User> users;
        if ("SYSTEM_ADMIN".equals(actorRole)) {
            users = userRepository.findAll();
        } else if ("COMPANY_HEAD".equals(actorRole)) {
            users = userRepository.findAllByCompanyId(actorCompanyId);
        } else {
            throw new InvalidStateException("Access denied");
        }
        return users.stream()
                .filter(u -> !"DELETED".equals(u.getStatus()))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id, UserPrincipal principal) {
        String actorRole = UserPrincipal.getRole(principal);
        String actorCompanyId = UserPrincipal.getCompanyId(principal);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found"));

        if (!"DELETED".equals(user.getStatus())) {
            validateAccess(user, actorRole, actorCompanyId);
        } else {
            throw new BusinessException("User not found");
        }

        return mapToResponse(user);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request, UserPrincipal principal) {
        String actorRole = UserPrincipal.getRole(principal);
        String actorCompanyId = UserPrincipal.getCompanyId(principal);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found"));

        if ("DELETED".equals(user.getStatus())) {
            throw new BusinessException("User not found");
        }

        validateAccess(user, actorRole, actorCompanyId);

        if (request.fullName() != null) {
            user.setFullName(request.fullName());
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        if (request.role() != null) {
            // Cannot assign SYSTEM_ADMIN unless SYSTEM_ADMIN
            if ("SYSTEM_ADMIN".equals(request.role()) && !"SYSTEM_ADMIN".equals(actorRole)) {
                throw new InvalidStateException("Cannot assign administrative role");
            }
            user.setRole(request.role());
        }
        if (request.status() != null) {
            user.setStatus(request.status());
        }

        user = userRepository.save(user);
        return mapToResponse(user);
    }

    @Transactional
    public void deleteUser(UUID id, UserPrincipal principal) {
        String actorRole = UserPrincipal.getRole(principal);
        String actorCompanyId = UserPrincipal.getCompanyId(principal);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found"));

        validateAccess(user, actorRole, actorCompanyId);

        user.setStatus("DELETED");
        userRepository.save(user);
    }

    private void validateCreatePermission(CreateUserRequest request, String actorRole, String actorCompanyId) {
        if ("SYSTEM_ADMIN".equals(actorRole)) {
            return;
        }
        if ("COMPANY_HEAD".equals(actorRole)) {
            if (request.companyId() == null || !request.companyId().equals(actorCompanyId)) {
                throw new InvalidStateException("Cannot create user for another company");
            }
            if ("SYSTEM_ADMIN".equals(request.role())) {
                throw new InvalidStateException("Cannot assign system admin role");
            }
            return;
        }
        throw new InvalidStateException("Access denied");
    }

    private void validateAccess(User targetUser, String actorRole, String actorCompanyId) {
        if ("SYSTEM_ADMIN".equals(actorRole)) {
            return;
        }
        if ("COMPANY_HEAD".equals(actorRole)) {
            if (targetUser.getCompanyId() != null && targetUser.getCompanyId().equals(actorCompanyId)) {
                return;
            }
        }
        throw new InvalidStateException("Access denied");
    }

    private UserResponse mapToResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole(),
                user.getCompanyId(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }
}
