package az.millikart.auth.service;

import az.millikart.auth.domain.User;
import az.millikart.auth.dto.CreateUserRequest;
import az.millikart.auth.dto.UpdateUserRequest;
import az.millikart.auth.dto.UserResponse;
import az.millikart.auth.repository.CompanyRepository;
import az.millikart.auth.repository.UserRepository;
import az.millikart.auth.repository.UserTerminalRepository;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
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
    private static final String STATUS_BLOCKED = "BLOCKED";

    // Роли, которые выдаёт и правит руководитель (project_docs/modules/auth.md §4.2). AUDITOR и
    // SYSTEM_ADMIN глобальны: их выдача или смена пароля такой учётке открыла бы ему все компании.
    private static final Set<Role> HEAD_MANAGED_ROLES = EnumSet.of(Role.COMPANY_MANAGER, Role.COMPANY_EMPLOYEE);

    // Роли только внутри компании: без companyId у них нет ни одного терминала (Р-90).
    private static final Set<Role> COMPANY_ROLES =
            EnumSet.of(Role.COMPANY_HEAD, Role.COMPANY_MANAGER, Role.COMPANY_EMPLOYEE);

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordHistoryService passwordHistory;
    private final UserTerminalRepository userTerminals;

    public UserService(UserRepository userRepository,
                       CompanyRepository companyRepository,
                       PasswordEncoder passwordEncoder,
                       RefreshTokenService refreshTokenService,
                       AuditLogService auditLogService,
                       ApplicationEventPublisher eventPublisher,
                       PasswordHistoryService passwordHistory,
                       UserTerminalRepository userTerminals) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
        this.passwordHistory = passwordHistory;
        this.userTerminals = userTerminals;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request, UserPrincipal principal) {
        Role actorRole = UserPrincipal.getRole(principal);
        String actorCompanyId = UserPrincipal.getCompanyId(principal);
        String actorUsername = UserPrincipal.getUsername(principal);

        String cleanEmail = request.username() != null ? request.username().trim().toLowerCase() : "";

        log.info("Request to create user: username={}, role={}, companyId={}",
                cleanEmail, request.role(), request.companyId());

        requireActiveActor(principal, AuditAction.CREATE, cleanEmail);
        validateCreatePermission(request, principal);

        if (userRepository.findByUsername(cleanEmail).isPresent()) {
            log.warn("User creation failed: username {} already exists", cleanEmail);
            throw new BusinessException("Username already exists");
        }

        // Роль компании без компании запрещена и здесь, как в правке (Р-90, Р-103).
        if (COMPANY_ROLES.contains(Role.fromValue(request.role()).orElse(null))
                && (request.companyId() == null || request.companyId().isBlank())) {
            throw new BusinessException("Role " + request.role() + " requires a company");
        }

        // Пустая строка — «без компании», как в правке: "" ушло бы в users.company_id и упало на внешнем ключе.
        String companyId = request.companyId() == null || request.companyId().isBlank() ? null : request.companyId();
        if (companyId != null && !liveCompanyExists(companyId)) {
            log.warn("User creation failed: companyId {} not found", companyId);
            throw new BusinessException("Company not found");
        }

        // Р-131: сотрудник без терминалов не заводится; другим ролям терминалы не назначаются.
        List<Integer> terminalIds = normalizedTerminals(request.terminalIds());
        boolean employee = Role.fromValue(request.role()).orElse(null) == Role.COMPANY_EMPLOYEE;
        if (employee) {
            requireCompanyTerminals(terminalIds, companyId);
        } else if (!terminalIds.isEmpty()) {
            throw new BusinessException(TERMINALS_FOR_EMPLOYEES_ONLY);
        }

        User user = User.builder()
                .username(cleanEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(request.role())
                .companyId(companyId)
                .status(STATUS_ACTIVE)
                // Пароль задал не владелец — сменит при первом входе (PCI DSS 8.3.5, Р-100).
                .passwordChangeRequired(true)
                .build();

        // saveAndFlush: назначения пишутся JDBC мимо Hibernate, и строка users должна быть в базе раньше них —
        // иначе внешний ключ user_terminals отвергнет вставку.
        user = userRepository.saveAndFlush(user);
        if (employee) {
            userTerminals.replace(user.getId(), terminalIds, actorUsername, Instant.now());
        }

        eventPublisher.publishEvent(AuditEvent.of(AuditEntity.USER, user.getId().toString(), AuditAction.CREATE,
                actorUsername, user.getCompanyId(),
                "Created user " + user.getUsername() + " with role " + user.getRole()
                        + (user.getCompanyId() != null ? " in company " + user.getCompanyId() : "")
                        + (employee ? " with terminals " + terminalIds : "")));

        log.info("User created successfully: id={}, username={}", user.getId(), user.getUsername());
        return mapToResponse(user, employee ? terminalIds : List.of());
    }

    // Поиск, фильтры, отсев удалённых, порядок и скоуп компании — в запросе, не пост-фильтром (P2-1, P3-1).
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
                // null-скоуп в запросе значит «все»: руководитель без компании видит пустой список (P3-1a).
                return PagedResponse.of(Page.empty(pageable), List.of());
            }
            companyScope = actorCompanyId;
        } else {
            auditLogService.logDenied(AuditEntity.USER, "ALL", AuditAction.LIST, UserPrincipal.getUsername(principal),
                    actorCompanyId, "Denied: role " + UserPrincipal.getRawRole(principal) + " attempted to list users");
            throw new InvalidStateException("Access denied");
        }

        Pageable pageOnly = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<User> page = userRepository.search(companyScope, role,
                SearchTerms.toLikePattern(search), pageOnly);

        // Терминалы — одним запросом на страницу, а не на строку.
        Map<UUID, List<Integer>> terminals = page.getContent().isEmpty()
                ? Map.of()
                : userTerminals.terminalIdsOf(page.getContent().stream().map(User::getId).toList());
        return PagedResponse.of(page, page.getContent().stream()
                .map(user -> mapToResponse(user, terminals.getOrDefault(user.getId(), List.of())))
                .collect(Collectors.toList()));
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id, UserPrincipal principal) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found"));

        if (!STATUS_DELETED.equals(user.getStatus())) {
            validateAccess(user, principal, AuditAction.READ);
        } else {
            throw new BusinessException("User not found");
        }

        return mapToResponse(user, userTerminals.terminalIdsOf(user.getId()));
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
        // Правкой ставятся только ACTIVE и BLOCKED (Р-103): DELETED — удаление со своей записью в журнале.
        if (request.status() != null && !STATUS_ACTIVE.equals(request.status()) && !STATUS_BLOCKED.equals(request.status())) {
            throw new BusinessException("User status must be ACTIVE or BLOCKED");
        }

        // Журнал называет поля, роль и компанию — «с чего на что» (P2-14); пароль — только фактом.
        List<String> changes = new ArrayList<>();
        boolean passwordChanged = false;
        boolean roleOrCompanyChanged = false;
        boolean roleChanged = false;
        boolean companyChanged = false;
        String previousStatus = user.getStatus();

        if (request.fullName() != null && !request.fullName().equals(user.getFullName())) {
            changes.add("fullName");
            user.setFullName(request.fullName());
        }
        boolean passwordResetByOther = false;
        if (request.password() != null && !request.password().isBlank()) {
            passwordResetByOther = !user.getId().toString().equals(UserPrincipal.getUserId(principal));
            // Свой пароль не повторяет четырёх последних (Р-102). На чужом проверки нет: отказ сказал бы
            // администратору, какие пароли пользователь недавно использовал.
            if (!passwordResetByOther) {
                passwordHistory.requireNotRecent(user, request.password());
            }
            passwordHistory.rememberCurrent(user, Instant.now());
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            // Сброс чужого пароля — смена при следующем входе (Р-100); свой пароль владелец задал сам.
            user.setPasswordChangeRequired(passwordResetByOther);
            changes.add(passwordResetByOther ? "password (to be changed at next sign-in)" : "password");
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
                roleOrCompanyChanged = true;
                roleChanged = true;
            }
        }
        // Переводит между компаниями только SYSTEM_ADMIN: руководитель ограничен своей (Р-90).
        if (request.companyId() != null) {
            String requestedCompanyId = request.companyId().isBlank() ? null : request.companyId().trim();
            if (!Objects.equals(requestedCompanyId, user.getCompanyId())) {
                if (actorRole != Role.SYSTEM_ADMIN) {
                    auditLogService.logDenied(AuditEntity.USER, id.toString(), AuditAction.UPDATE, actorUsername, actorCompanyId,
                            "Denied: role " + UserPrincipal.getRawRole(principal)
                                    + " attempted to move user " + id + " to company " + requestedCompanyId);
                    throw new InvalidStateException("Cannot move a user to another company");
                }
                if (requestedCompanyId != null && !liveCompanyExists(requestedCompanyId)) {
                    throw new BusinessException("Company not found");
                }
                changes.add("companyId " + user.getCompanyId() + " -> " + requestedCompanyId);
                user.setCompanyId(requestedCompanyId);
                roleOrCompanyChanged = true;
                companyChanged = true;
            }
        }
        // Проверяется итог, а не запрос: к роли компании без компании ведут и смена роли, и снятие компании.
        // Только при их смене — старую запись без компании можно блокировать и переименовывать.
        // Присвоенное выше откатит транзакция.
        if (roleOrCompanyChanged
                && COMPANY_ROLES.contains(Role.fromValue(user.getRole()).orElse(null)) && user.getCompanyId() == null) {
            throw new BusinessException("Role " + user.getRole() + " requires a company");
        }

        // Р-131: итог проверяется, только когда терминалы, роль или компания менялись — сотрудника, заведённого
        // до назначений, можно переименовать и заблокировать, не раздавая ему терминалы. Не сотрудник
        // назначений не держит: ставший менеджером их теряет. Присвоенное выше откатит транзакция.
        List<Integer> currentTerminals = userTerminals.terminalIdsOf(user.getId());
        List<Integer> requestedTerminals = request.terminalIds() == null ? null : normalizedTerminals(request.terminalIds());
        List<Integer> targetTerminals;
        if (Role.fromValue(user.getRole()).orElse(null) == Role.COMPANY_EMPLOYEE) {
            if (requestedTerminals != null) {
                targetTerminals = requestedTerminals;
            } else {
                // Терминалы прежней компании новой не принадлежат; новый сотрудник назначений не имел.
                targetTerminals = companyChanged ? List.of() : currentTerminals;
            }
            if (requestedTerminals != null || roleChanged || companyChanged) {
                requireCompanyTerminals(targetTerminals, user.getCompanyId());
            }
        } else {
            if (requestedTerminals != null && !requestedTerminals.isEmpty()) {
                throw new BusinessException(TERMINALS_FOR_EMPLOYEES_ONLY);
            }
            targetTerminals = List.of();
        }
        boolean terminalsChanged = !targetTerminals.equals(currentTerminals);
        if (terminalsChanged) {
            changes.add("terminals " + currentTerminals + " -> " + targetTerminals);
        }

        boolean nonActiveStatusSet = false;
        if (request.status() != null) {
            nonActiveStatusSet = !STATUS_ACTIVE.equals(request.status());
            if (!request.status().equals(user.getStatus())) {
                changes.add("status " + user.getStatus() + " -> " + request.status());
                // Разблокировка — новый отсчёт до автоблокировки: иначе ближайший проход снова
                // заблокировал бы учётку, простоявшую 90 дней (Р-101).
                if (!nonActiveStatusSet) {
                    user.setLastActivityAt(Instant.now());
                }
            }
            user.setStatus(request.status());
        }

        user = userRepository.save(user);
        if (terminalsChanged) {
            userTerminals.replace(user.getId(), targetTerminals, actorUsername, Instant.now());
        }

        String actorUsername2 = actorUsername;
        eventPublisher.publishEvent(AuditEvent.of(AuditEntity.USER, user.getId().toString(), AuditAction.UPDATE,
                actorUsername2, user.getCompanyId(),
                changes.isEmpty() ? "No fields changed" : "Changed " + String.join(", ", changes)));

        // Пароль и статус — отдельные события: их ищут по действию, а не в details каждого UPDATE.
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

        // Сессии не-ACTIVE пользователя гасятся здесь, access-токены живут до срока. При любом
        // не-ACTIVE, а не только на переходе: массовый UPDATE идемпотентен.
        if (nonActiveStatusSet) {
            int revoked = refreshTokenService.revokeAllForUser(user.getId(), Instant.now());
            log.info("User {} changed status to {}: {} refresh token(s) revoked",
                    user.getId(), user.getStatus(), revoked);
        } else if (passwordChanged) {
            // Сессии со старым паролем гасятся при любой смене, как у /auth/change-password: и чужой сброс, и
            // свой пароль меняют чаще всего из-за утечки (PATCH-SELF-PASSWORD). Своя сессия тоже кончается.
            int revoked = refreshTokenService.revokeAllForUser(user.getId(), Instant.now());
            log.info("User {} password {}: {} refresh token(s) revoked", user.getId(),
                    passwordResetByOther ? "reset by another user" : "changed by its owner", revoked);
        }
        return mapToResponse(user, targetTerminals);
    }

    @Transactional
    public void deleteUser(UUID id, UserPrincipal principal) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found"));

        requireActiveActor(principal, AuditAction.DELETE, id.toString());
        validateWriteAccess(user, principal, AuditAction.DELETE);

        user.setStatus(STATUS_DELETED);
        userRepository.save(user);
        // Живой access-токен удалённого сотрудника доживает 15 минут — без назначений он не видит ничего (Р-131).
        userTerminals.replace(user.getId(), List.of(), UserPrincipal.getUsername(principal), Instant.now());

        eventPublisher.publishEvent(AuditEvent.of(AuditEntity.USER, user.getId().toString(), AuditAction.DELETE,
                UserPrincipal.getUsername(principal), user.getCompanyId(),
                "Soft deleted user " + user.getUsername() + " (role " + user.getRole() + ")"));

        int revoked = refreshTokenService.revokeAllForUser(user.getId(), Instant.now());
        log.info("User {} deleted: {} refresh token(s) revoked", user.getId(), revoked);
    }

    // Удалённая компания — как несуществующая: в неё не заводят и не переводят (Р-107).
    private boolean liveCompanyExists(String companyId) {
        return companyRepository.existsByIdAndStatusNot(companyId, STATUS_DELETED);
    }

    private void validateCreatePermission(CreateUserRequest request, UserPrincipal principal) {
        Role actorRole = UserPrincipal.getRole(principal);
        String actorCompanyId = UserPrincipal.getCompanyId(principal);
        if (actorRole == Role.SYSTEM_ADMIN) {
            return;
        }
        if (actorRole == Role.COMPANY_HEAD) {
            if (request.companyId() == null || !request.companyId().equals(actorCompanyId)) {
                auditLogService.logDenied(AuditEntity.USER, request.username(), AuditAction.CREATE,
                        UserPrincipal.getUsername(principal), actorCompanyId,
                        "Denied: role " + UserPrincipal.getRawRole(principal)
                                + " attempted to create a user in company " + request.companyId());
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
        auditLogService.logDenied(AuditEntity.USER, request.username(), AuditAction.CREATE,
                UserPrincipal.getUsername(principal), actorCompanyId,
                "Denied: role " + UserPrincipal.getRawRole(principal) + " attempted to create a user");
        throw new InvalidStateException("Access denied");
    }

    // Правка и удаление: руководитель трогает в своей компании только роли ниже своей и себя самого.
    private void validateWriteAccess(User targetUser, UserPrincipal principal, String action) {
        Role actorRole = UserPrincipal.getRole(principal);
        validateAccess(targetUser, principal, action);
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

    // Access-токен живёт до 15 минут после блокировки (project_docs/modules/auth.md §4.1.2): без сверки
    // с базой заблокированный актор успел бы разблокировать себя или завести учётку. Строки нет только
    // у статического токена интеграции и в синтетических тестах.
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

    // Компанию цели в details не писать: руководитель читает журнал своей компании и узнал бы, чей это UUID.
    private void validateAccess(User targetUser, UserPrincipal principal, String action) {
        Role actorRole = UserPrincipal.getRole(principal);
        String actorCompanyId = UserPrincipal.getCompanyId(principal);
        if (actorRole == Role.SYSTEM_ADMIN) {
            return;
        }
        if (actorRole == Role.COMPANY_HEAD
                && targetUser.getCompanyId() != null && targetUser.getCompanyId().equals(actorCompanyId)) {
            return;
        }
        auditLogService.logDenied(AuditEntity.USER, targetUser.getId().toString(), action,
                UserPrincipal.getUsername(principal), actorCompanyId,
                "Denied: role " + UserPrincipal.getRawRole(principal) + " of company " + actorCompanyId
                        + " attempted " + action + " of user " + targetUser.getId() + " outside its company");
        throw new InvalidStateException("Access denied");
    }

    private UserResponse mapToResponse(User user, List<Integer> terminalIds) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole(),
                user.getCompanyId(),
                user.getStatus(),
                user.getCreatedAt(),
                user.isPasswordChangeRequired(),
                terminalIds
        );
    }

    private static final String TERMINALS_FOR_EMPLOYEES_ONLY = "Terminals are assigned to employees only";

    // Без повторов и по возрастанию: так список сравнивается с назначенным и так же пишется в журнал.
    private static List<Integer> normalizedTerminals(List<Integer> terminalIds) {
        return terminalIds == null ? List.of() : List.copyOf(new TreeSet<>(terminalIds));
    }

    // Р-131: сотруднику — хотя бы один терминал, и все — его компании. Чужой номер называется в отказе:
    // администратор выбирает компанию и терминалы в одной форме и мог ошибиться.
    private void requireCompanyTerminals(List<Integer> terminalIds, String companyId) {
        if (terminalIds.isEmpty()) {
            throw new BusinessException("An employee needs at least one terminal");
        }
        Set<Integer> owned = userTerminals.ownedByCompany(companyId, terminalIds);
        List<Integer> foreign = terminalIds.stream().filter(id -> !owned.contains(id)).toList();
        if (!foreign.isEmpty()) {
            throw new BusinessException("Terminals " + foreign + " do not belong to company " + companyId);
        }
    }
}
