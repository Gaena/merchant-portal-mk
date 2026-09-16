package az.millikart.auth.service;

import az.millikart.auth.domain.User;
import az.millikart.auth.dto.CreateUserRequest;
import az.millikart.auth.dto.UpdateUserRequest;
import az.millikart.auth.dto.UserResponse;
import az.millikart.auth.repository.CompanyRepository;
import az.millikart.auth.repository.UserRepository;
import az.millikart.common.audit.AuditAction;
import az.millikart.common.audit.AuditEntity;
import az.millikart.common.audit.AuditEvent;
import az.millikart.common.audit.AuditLogService;
import az.millikart.common.dto.PagedResponse;
import az.millikart.common.exception.BusinessException;
import az.millikart.common.exception.InvalidStateException;
import az.millikart.common.search.SearchTerms;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millikart.common.security.Role;
import az.millikart.common.security.UserPrincipal;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DELETED = "DELETED";

    // Руководитель выдаёт и правит только роли ниже своей (auth.md §4.2). AUDITOR и SYSTEM_ADMIN
    // глобальны: выдать такую роль или сменить пароль такой учётке с его companyId значило бы
    // получить данные всех компаний или права администратора.
    private static final Set<Role> HEAD_MANAGED_ROLES = EnumSet.of(Role.COMPANY_MANAGER, Role.COMPANY_EMPLOYEE);

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;

    public UserService(UserRepository userRepository,
                       CompanyRepository companyRepository,
                       PasswordEncoder passwordEncoder,
                       RefreshTokenService refreshTokenService,
                       AuditLogService auditLogService,
                       ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request, UserPrincipal principal) {
        Role actorRole = UserPrincipal.getRole(principal);
        String actorCompanyId = UserPrincipal.getCompanyId(principal);
        String actorUsername = UserPrincipal.getUsername(principal);

        String cleanEmail = request.username() != null ? request.username().trim().toLowerCase() : "";

        log.info("Request to create user: username={}, role={}, companyId={} by actor: {}",
                cleanEmail, request.role(), request.companyId(), actorUsername);

        requireActiveActor(principal, AuditAction.CREATE, cleanEmail);
        // Enforce RBAC
        validateCreatePermission(request, principal);

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
                .status(STATUS_ACTIVE)
                .build();

        user = userRepository.save(user);

        // Роль — главное в этой записи: здесь человек впервые получает права.
        eventPublisher.publishEvent(AuditEvent.of(AuditEntity.USER, user.getId().toString(), AuditAction.CREATE,
                actorUsername, user.getCompanyId(),
                "Created user " + user.getUsername() + " with role " + user.getRole()
                        + (user.getCompanyId() != null ? " in company " + user.getCompanyId() : "")));

        log.info("User created successfully: id={}, username={}", user.getId(), user.getUsername());
        return mapToResponse(user);
    }

    // Страница, поиск, фильтр по роли, отсев удалённых и порядок — всё в запросе (P2-1, P3-1):
    // поиск, видящий только текущую страницу, не находит никого. Скоуп компании — параметр
    // запроса, а не пост-фильтр: поиском его не обойти.
    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> listUsers(Pageable pageable, UserPrincipal principal,
                                                 String search, String role) {
        Role actorRole = UserPrincipal.getRole(principal);
        String actorCompanyId = UserPrincipal.getCompanyId(principal);

        String companyScope;
        if (actorRole == Role.SYSTEM_ADMIN) {
            companyScope = null;
        } else if (actorRole == Role.COMPANY_HEAD) {
            if (actorCompanyId == null) {
                // Руководитель без компании и раньше видел пустой список (company_id = NULL не
                // совпадает ни с чем) и должен видеть его дальше: в запросе null-скоуп означает
                // «все», а прав на всех у него нет. Сторож — тест
                // companyHeadWithoutCompany_seesNobody_notEveryone (P3-1a).
                return PagedResponse.of(Page.empty(pageable), List.of());
            }
            companyScope = actorCompanyId;
        } else {
            throw new InvalidStateException("Access denied");
        }

        Pageable pageOnly = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<User> page = userRepository.search(companyScope, role,
                SearchTerms.toLikePattern(search), pageOnly);

        return PagedResponse.of(page, page.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList()));
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id, UserPrincipal principal) {
        Role actorRole = UserPrincipal.getRole(principal);
        String actorCompanyId = UserPrincipal.getCompanyId(principal);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found"));

        if (!STATUS_DELETED.equals(user.getStatus())) {
            validateAccess(user, actorRole, actorCompanyId);
        } else {
            throw new BusinessException("User not found");
        }

        return mapToResponse(user);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request, UserPrincipal principal) {
        Role actorRole = UserPrincipal.getRole(principal);
        String actorCompanyId = UserPrincipal.getCompanyId(principal);
        String actorUsername = UserPrincipal.getUsername(principal);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found"));

        if (STATUS_DELETED.equals(user.getStatus())) {
            throw new BusinessException("User not found");
        }

        requireActiveActor(principal, AuditAction.UPDATE, id.toString());
        validateWriteAccess(user, principal, AuditAction.UPDATE);

        // Поля перечисляются поимённо: «пользователь обновлён» бесполезно, а смена роли или
        // компании — смена прав, и запись обязана сказать, с чего на что (P2-14). Пароль
        // отмечается фактом, никогда значением.
        List<String> changes = new ArrayList<>();
        boolean passwordChanged = false;
        String previousStatus = user.getStatus();

        if (request.fullName() != null && !request.fullName().equals(user.getFullName())) {
            changes.add("fullName");
            user.setFullName(request.fullName());
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            changes.add("password");
            passwordChanged = true;
        }
        if (request.role() != null) {
            // Не администратор выдаёт только роли ниже руководителя; неизменённая роль — не выдача.
            boolean grantable = actorRole == Role.SYSTEM_ADMIN
                    || request.role().equals(user.getRole())
                    || HEAD_MANAGED_ROLES.contains(Role.fromValue(request.role()).orElse(null));
            if (!grantable) {
                auditLogService.logDenied(AuditEntity.USER, id.toString(), AuditAction.UPDATE, actorUsername, actorCompanyId,
                        "Denied: role " + UserPrincipal.getRawRole(principal)
                                + " attempted to grant role " + request.role() + " to user " + id);
                throw new InvalidStateException("Cannot assign this role");
            }
            if (!request.role().equals(user.getRole())) {
                changes.add("role " + user.getRole() + " -> " + request.role());
                user.setRole(request.role());
            }
        }
        boolean nonActiveStatusSet = false;
        if (request.status() != null) {
            nonActiveStatusSet = !STATUS_ACTIVE.equals(request.status());
            if (!request.status().equals(user.getStatus())) {
                changes.add("status " + user.getStatus() + " -> " + request.status());
            }
            user.setStatus(request.status());
        }

        user = userRepository.save(user);

        String actorUsername2 = actorUsername;
        eventPublisher.publishEvent(AuditEvent.of(AuditEntity.USER, user.getId().toString(), AuditAction.UPDATE,
                actorUsername2, user.getCompanyId(),
                changes.isEmpty() ? "No fields changed" : "Changed " + String.join(", ", changes)));

        // Смена пароля и смена состояния аккаунта — свои события: их ищут по действию, иначе
        // пришлось бы вычитывать details каждого UPDATE.
        if (passwordChanged) {
            eventPublisher.publishEvent(AuditEvent.of(AuditEntity.USER, user.getId().toString(),
                    AuditAction.PASSWORD_CHANGE, actorUsername2, user.getCompanyId(),
                    "Password changed for " + user.getUsername()));
        }
        if (request.status() != null && !request.status().equals(previousStatus)) {
            boolean reactivated = STATUS_ACTIVE.equals(request.status());
            eventPublisher.publishEvent(AuditEvent.of(AuditEntity.USER, user.getId().toString(),
                    reactivated ? AuditAction.UNBLOCK : AuditAction.BLOCK, actorUsername2, user.getCompanyId(),
                    (reactivated ? "Account reactivated" : "Account set to " + request.status())
                            + " for " + user.getUsername() + " (was " + previousStatus + ")"));
        }

        // Не-ACTIVE пользователь не должен продлевать сессию через refresh. Refresh проверяет
        // статус и сам, но только когда токен предъявят, — сессии заканчивает вот это. Уже выданные
        // access-токены живут до своего срока. Гасим при любом не-ACTIVE, а не только на переходе:
        // массовый UPDATE идемпотентен.
        if (nonActiveStatusSet) {
            int revoked = refreshTokenService.revokeAllForUser(user.getId(), Instant.now());
            log.info("User {} changed status to {} by {}: {} refresh token(s) revoked",
                    user.getId(), user.getStatus(), UserPrincipal.getUsername(principal), revoked);
        }
        return mapToResponse(user);
    }

    @Transactional
    public void deleteUser(UUID id, UserPrincipal principal) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found"));

        requireActiveActor(principal, AuditAction.DELETE, id.toString());
        validateWriteAccess(user, principal, AuditAction.DELETE);

        user.setStatus(STATUS_DELETED);
        userRepository.save(user);

        eventPublisher.publishEvent(AuditEvent.of(AuditEntity.USER, user.getId().toString(), AuditAction.DELETE,
                UserPrincipal.getUsername(principal), user.getCompanyId(),
                "Soft deleted user " + user.getUsername() + " (role " + user.getRole() + ")"));

        // Мягкое удаление заканчивает все сессии пользователя — то же правило, что в updateUser.
        int revoked = refreshTokenService.revokeAllForUser(user.getId(), Instant.now());
        log.info("User {} deleted by {}: {} refresh token(s) revoked",
                user.getId(), UserPrincipal.getUsername(principal), revoked);
    }

    private void validateCreatePermission(CreateUserRequest request, UserPrincipal principal) {
        Role actorRole = UserPrincipal.getRole(principal);
        String actorCompanyId = UserPrincipal.getCompanyId(principal);
        if (actorRole == Role.SYSTEM_ADMIN) {
            return;
        }
        if (actorRole == Role.COMPANY_HEAD) {
            if (request.companyId() == null || !request.companyId().equals(actorCompanyId)) {
                throw new InvalidStateException("Cannot create user for another company");
            }
            if (!HEAD_MANAGED_ROLES.contains(Role.fromValue(request.role()).orElse(null))) {
                auditLogService.logDenied(AuditEntity.USER, request.username(), AuditAction.CREATE,
                        UserPrincipal.getUsername(principal), actorCompanyId,
                        "Denied: role " + UserPrincipal.getRawRole(principal)
                                + " attempted to create a user with role " + request.role());
                throw new InvalidStateException("Cannot assign this role");
            }
            return;
        }
        throw new InvalidStateException("Access denied");
    }

    // Правка и удаление: руководитель трогает в своей компании только роли ниже своей и себя самого.
    private void validateWriteAccess(User targetUser, UserPrincipal principal, String action) {
        Role actorRole = UserPrincipal.getRole(principal);
        validateAccess(targetUser, actorRole, UserPrincipal.getCompanyId(principal));
        if (actorRole == Role.COMPANY_HEAD
                && !targetUser.getId().toString().equals(UserPrincipal.getUserId(principal))
                && !HEAD_MANAGED_ROLES.contains(Role.fromValue(targetUser.getRole()).orElse(null))) {
            auditLogService.logDenied(AuditEntity.USER, targetUser.getId().toString(), action,
                    UserPrincipal.getUsername(principal), UserPrincipal.getCompanyId(principal),
                    "Denied: role " + UserPrincipal.getRawRole(principal) + " attempted " + action
                            + " of user " + targetUser.getId() + " with role " + targetUser.getRole());
            throw new InvalidStateException("Access denied");
        }
    }

    // Access-токен живёт до 15 минут после блокировки или удаления (auth.md §4.1.2): без сверки с
    // базой заблокированный руководитель или админ за это время снял бы блокировку с себя или завёл
    // бы себе новую учётку. Строки нет только у статического токена интеграции и в синтетических тестах.
    private void requireActiveActor(UserPrincipal principal, String action, String entityId) {
        User actor = parseUuid(UserPrincipal.getUserId(principal))
                .flatMap(userRepository::findById)
                .orElse(null);
        if (actor != null && !STATUS_ACTIVE.equals(actor.getStatus())) {
            auditLogService.logDenied(AuditEntity.USER, entityId, action,
                    UserPrincipal.getUsername(principal), UserPrincipal.getCompanyId(principal),
                    "Denied: actor account is " + actor.getStatus());
            throw new InvalidStateException("Access denied");
        }
    }

    private static Optional<UUID> parseUuid(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private void validateAccess(User targetUser, Role actorRole, String actorCompanyId) {
        if (actorRole == Role.SYSTEM_ADMIN) {
            return;
        }
        if (actorRole == Role.COMPANY_HEAD) {
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
