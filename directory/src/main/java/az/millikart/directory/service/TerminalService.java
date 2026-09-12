package az.millikart.directory.service;

import az.millikart.common.dto.PagedResponse;
import az.millikart.common.exception.BusinessException;
import az.millikart.common.exception.InvalidStateException;
import az.millikart.directory.domain.Terminal;
import az.millikart.directory.domain.TerminalStatus;
import az.millikart.directory.domain.TerminalStatusSource;
import az.millikart.directory.dto.CreateTerminalRequest;
import az.millikart.directory.dto.TerminalOptionResponse;
import az.millikart.directory.dto.TerminalPasswordResponse;
import az.millikart.directory.dto.TerminalResponse;
import az.millikart.directory.dto.UpdateTerminalRequest;
import az.millikart.directory.repository.CompanyRepository;
import az.millikart.directory.repository.PaymentLinkStatusRepository;
import az.millikart.directory.repository.ProviderTerminalStatusRepository;
import az.millikart.directory.repository.TerminalRepository;
import az.millikart.common.audit.AuditAction;
import az.millikart.common.audit.AuditEntity;
import az.millikart.common.audit.AuditEvent;
import az.millikart.common.audit.AuditLogService;
import az.millikart.common.search.SearchTerms;
import az.millikart.common.security.Role;
import az.millikart.common.security.UserPrincipal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TerminalService {

    private static final Logger log = LoggerFactory.getLogger(TerminalService.class);

    private static final Set<Role> TERMINAL_WRITE_ROLES =
            EnumSet.of(Role.SYSTEM_ADMIN, Role.COMPANY_HEAD, Role.COMPANY_MANAGER);

    private final TerminalRepository terminalRepository;
    private final CompanyRepository companyRepository;
    private final PaymentLinkStatusRepository paymentLinkStatusRepository;
    private final ProviderTerminalStatusRepository providerTerminals;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;

    public TerminalService(TerminalRepository terminalRepository,
                           CompanyRepository companyRepository,
                           PaymentLinkStatusRepository paymentLinkStatusRepository,
                           ProviderTerminalStatusRepository providerTerminals,
                           AuditLogService auditLogService,
                           ApplicationEventPublisher eventPublisher) {
        this.terminalRepository = terminalRepository;
        this.companyRepository = companyRepository;
        this.paymentLinkStatusRepository = paymentLinkStatusRepository;
        this.providerTerminals = providerTerminals;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public TerminalResponse createTerminal(CreateTerminalRequest request, UserPrincipal principal) {
        String actorUsername = UserPrincipal.getUsername(principal);

        log.info("Request to create terminal: id={}, name={}, companyId={} by actor: {}",
                request.id(), request.name(), request.companyId(), actorUsername);

        validateWriteAccessToCompany(request.companyId(), principal,
                String.valueOf(request.id()), AuditAction.CREATE,
                "create terminal " + request.id() + " for company " + request.companyId());

        if (!companyRepository.existsById(request.companyId())) {
            throw new BusinessException("Company with ID '" + request.companyId() + "' not found");
        }

        if (terminalRepository.existsById(request.id())) {
            throw new BusinessException("Terminal with ID " + request.id() + " already exists");
        }

        // Название и логин берутся у провайдера, когда указан его терминал: он их хозяин, и
        // введённые руками однажды разойдутся с тем, чем терминал ходит в шлюз.
        String name = request.name();
        String login = request.login();
        String providerRid = trimToNull(request.providerRid());
        if (providerRid != null) {
            // Один терминал провайдера — одна наша компания. Иначе две компании смотрели бы
            // в одну выписку, и каждая видела бы платежи другой.
            terminalRepository.findByProviderRid(providerRid).ifPresent(existing -> {
                throw new BusinessException("Provider terminal " + providerRid
                        + " is already linked to terminal " + existing.getId());
            });
            ProviderTerminalStatusRepository.ProviderTerminalRow row = providerTerminals
                    .findByRid(providerRid)
                    .orElseThrow(() -> new BusinessException(
                            "Provider terminal " + providerRid + " is not in the synchronised list"));
            name = row.title();
            login = row.login();
        }
        if (name == null || name.isBlank() || login == null || login.isBlank()) {
            throw new BusinessException("Terminal name and login are required unless providerRid is given");
        }

        Terminal terminal = Terminal.builder()
                .id(request.id())
                .name(name)
                .login(login)
                .password(request.password())
                .companyId(request.companyId())
                .providerRid(providerRid)
                .createdBy(actorUsername)
                .updatedBy(actorUsername)
                .build();

        terminal = terminalRepository.save(terminal);

        // Пишется AuditLogWriter после коммита этой транзакции (Р-35).
        eventPublisher.publishEvent(AuditEvent.of(
                AuditEntity.TERMINAL,
                terminal.getId().toString(),
                AuditAction.CREATE,
                actorUsername,
                terminal.getCompanyId(),
                "Created terminal: " + terminal.getName() + " for company " + terminal.getCompanyId()
        ));

        return mapToResponse(terminal);
    }

    // Страница терминалов (P2-1) с поиском по name, login, id, companyId и имени компании (P3-1).
    // Сортировка name + id: без уникального довеска записи с равным именем прыгают между
    // страницами. Скоуп компании — условие запроса, поиск его не обходит: чужой терминал по имени
    // не находится.
    @Transactional(readOnly = true)
    public PagedResponse<TerminalResponse> listTerminals(Pageable pageable, UserPrincipal principal,
                                                         String search) {
        Pageable byName = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));

        String companyScope = isGlobalReader(principal) ? null : requireOwnCompany(principal);
        Page<Terminal> page = terminalRepository.search(companyScope,
                SearchTerms.toLikePattern(search), byName);

        return PagedResponse.of(page, page.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList()));
    }

    // Р-45: лёгкий фид для селекторов — id, name, login, status, без страниц. Заблокированные
    // терминалы отдаются намеренно, фильтрует потребитель: форме ссылки нужны только ACTIVE (бэкенд
    // всё равно откажет по заблокированному, P2-8), а экрану транзакций — все, иначе старый платёж
    // теряет имя своего терминала. Фильтр на сервере обслужил бы первого и сломал второго.
    // Про логин в ответе — см. комментарий у TerminalOptionResponse: ворота те же, что у полного
    // списка, который логин отдаёт и так, а пароль сюда не попадает.
    @Transactional(readOnly = true)
    public List<TerminalOptionResponse> listTerminalOptions(UserPrincipal principal) {
        List<Terminal> terminals = isGlobalReader(principal)
                ? terminalRepository.findAllByOrderByNameAscIdAsc()
                : terminalRepository.findAllByCompanyIdOrderByNameAscIdAsc(requireOwnCompany(principal));

        return terminals.stream()
                .map(t -> new TerminalOptionResponse(t.getId(), t.getName(), t.getLogin(), t.getStatus()))
                .collect(Collectors.toList());
    }

    // Возвращает не только флаг: роль без права на список получает отказ прямо здесь.
    private boolean isGlobalReader(UserPrincipal principal) {
        Role actorRole = UserPrincipal.getRole(principal);
        if (actorRole == Role.SYSTEM_ADMIN || actorRole == Role.AUDITOR) {
            return true;
        }
        if (actorRole == Role.COMPANY_HEAD || actorRole == Role.COMPANY_MANAGER
                || actorRole == Role.COMPANY_EMPLOYEE) {
            return false;
        }
        auditLogService.logDenied(AuditEntity.TERMINAL, "ALL", AuditAction.LIST,
                UserPrincipal.getUsername(principal), UserPrincipal.getCompanyId(principal),
                "Denied: role " + UserPrincipal.getRawRole(principal)
                        + " attempted to list terminals");
        throw new InvalidStateException("Access denied");
    }

    // Читатель без компании получает отказ, а не null.
    private String requireOwnCompany(UserPrincipal principal) {
        String actorCompanyId = UserPrincipal.getCompanyId(principal);
        if (actorCompanyId == null) {
            auditLogService.logDenied(AuditEntity.TERMINAL, "ALL", AuditAction.LIST,
                    UserPrincipal.getUsername(principal), null,
                    "Denied: " + UserPrincipal.getRole(principal)
                            + " without a company attempted to list terminals");
            throw new InvalidStateException("Access denied: User not assigned to a company");
        }
        return actorCompanyId;
    }

    /**
     * Пароль терминала как есть — для того, чтобы администратор мог его посмотреть, не заводя
     * терминал заново.
     *
     * Три вещи, которые здесь обязательны и вместе делают это допустимым:
     *
     *   1. **Только SYSTEM_ADMIN.** Роли записи (COMPANY_HEAD, COMPANY_MANAGER) пароль менять
     *      больше не могут и увидеть его не могут тоже: это ключ от эквайринга, а не настройка
     *      терминала. Отказ пишется в журнал — попытка посмотреть чужой платёжный ключ это ровно
     *      то событие, ради которого журнал и заведён.
     *   2. **Отдельный запрос.** В `TerminalResponse` пароль остаётся `"********"`, поэтому ни
     *      список, ни карточка, ни лёгкий фид его не несут, сколько бы экранов их ни читало.
     *   3. **След у каждого чтения.** Пишется сразу, своей транзакцией: читать тут нечего
     *      коммитить, а запись «кто и когда посмотрел пароль терминала» нужна именно в момент
     *      чтения.
     *
     * Сам пароль в журнал, разумеется, не идёт (см. AuditLogService.logDenied о секретах
     * в details).
     */
    @Transactional(readOnly = true)
    public TerminalPasswordResponse revealPassword(Integer id, UserPrincipal principal) {
        Terminal terminal = terminalRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Terminal not found"));

        String actorUsername = UserPrincipal.getUsername(principal);
        if (UserPrincipal.getRole(principal) != Role.SYSTEM_ADMIN) {
            auditLogService.logDenied(AuditEntity.TERMINAL, String.valueOf(id), AuditAction.READ,
                    actorUsername, UserPrincipal.getCompanyId(principal),
                    "Denied: role " + UserPrincipal.getRawRole(principal)
                            + " attempted to reveal the acquiring password of terminal " + id);
            throw new InvalidStateException("Access denied");
        }

        auditLogService.recordSuccess(AuditEvent.of(
                AuditEntity.TERMINAL,
                String.valueOf(id),
                AuditAction.READ,
                actorUsername,
                terminal.getCompanyId(),
                "Revealed the acquiring password of terminal " + id
        ));

        return new TerminalPasswordResponse(terminal.getId(), terminal.getPassword());
    }

    @Transactional(readOnly = true)
    public TerminalResponse getTerminal(Integer id, UserPrincipal principal) {
        Terminal terminal = terminalRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Terminal not found"));

        validateReadAccessToCompany(terminal.getCompanyId(), principal, String.valueOf(id));
        return mapToResponse(terminal);
    }

    @Transactional
    public TerminalResponse updateTerminal(Integer id, UpdateTerminalRequest request, UserPrincipal principal) {
        String actorUsername = UserPrincipal.getUsername(principal);

        Terminal terminal = terminalRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Terminal not found"));

        validateWriteAccessToCompany(terminal.getCompanyId(), principal,
                String.valueOf(id), AuditAction.UPDATE,
                "update terminal " + id + " of company " + terminal.getCompanyId());

        StringBuilder changes = new StringBuilder();
        if (request.name() != null && !request.name().isBlank()) {
            changes.append("Name changed from '").append(terminal.getName()).append("' to '").append(request.name()).append("'. ");
            terminal.setName(request.name());
        }
        if (request.login() != null && !request.login().isBlank()) {
            changes.append("Login updated. ");
            terminal.setLogin(request.login());
        }
        if (request.password() != null && !request.password().isBlank()) {
            // Пароль эквайринга меняет только SYSTEM_ADMIN — те же ворота, что и на его чтение.
            // Молча проигнорировать нельзя: администратор компании решил бы, что пароль сменён,
            // и остался бы со старым ключом, считая его новым.
            if (UserPrincipal.getRole(principal) != Role.SYSTEM_ADMIN) {
                auditLogService.logDenied(AuditEntity.TERMINAL, String.valueOf(id), AuditAction.UPDATE,
                        actorUsername, UserPrincipal.getCompanyId(principal),
                        "Denied: role " + UserPrincipal.getRawRole(principal)
                                + " attempted to change the acquiring password of terminal " + id);
                throw new InvalidStateException("Access denied: only a system administrator may change the terminal password");
            }
            changes.append("Password updated. ");
            terminal.setPassword(request.password());
        }
        if (request.companyId() != null && !request.companyId().isBlank()) {
            validateWriteAccessToCompany(request.companyId(), principal,
                    String.valueOf(id), AuditAction.UPDATE,
                    "move terminal " + id + " to company " + request.companyId());
            if (!companyRepository.existsById(request.companyId())) {
                throw new BusinessException("Company with ID '" + request.companyId() + "' not found");
            }
            changes.append("CompanyId changed from '").append(terminal.getCompanyId()).append("' to '").append(request.companyId()).append("'. ");
            terminal.setCompanyId(request.companyId());
        }

        // Последним и отдельно: единственное поле, чья правка выходит за строку терминала.
        // Установка того же статуса — не изменение и не должна трогать ни одной ссылки, иначе
        // PATCH, возвращающий объект целиком, переприостанавливает ссылки на каждом сохранении.
        String statusChange = null;
        if (request.status() != null && request.status() != terminal.getStatus()) {
            // Терминал, выключенный синхронизацией, человек включить не может: у провайдера он
            // снят с обслуживания, платёж через него всё равно не пройдёт, а включение здесь
            // подняло бы его ссылки и отправило плательщиков в отказ. Вернёт его та же
            // синхронизация, когда провайдер вернёт терминал себе.
            if (request.status() == TerminalStatus.ACTIVE
                    && terminal.getStatus() == TerminalStatus.BLOCKED
                    && terminal.getStatusSource() == TerminalStatusSource.PROVIDER) {
                auditLogService.logDenied(AuditEntity.TERMINAL, String.valueOf(id), AuditAction.UNBLOCK,
                        actorUsername, terminal.getCompanyId(),
                        "Denied: terminal " + id + " was blocked by the provider sync and cannot be "
                                + "unblocked by hand");
                throw new InvalidStateException("Terminal " + id + " is out of service at the provider "
                        + "and will be unblocked automatically once the provider brings it back");
            }
            statusChange = applyStatusChange(terminal, request.status());
            // Статус поставил человек — и это решение синхронизация впредь не трогает.
            terminal.setStatusSource(TerminalStatusSource.MANUAL);
            changes.append(statusChange).append(". ");
        }

        terminal.setUpdatedBy(actorUsername);
        terminal = terminalRepository.save(terminal);

        // Пишется AuditLogWriter после коммита этой транзакции (Р-35).
        eventPublisher.publishEvent(AuditEvent.of(
                AuditEntity.TERMINAL,
                terminal.getId().toString(),
                AuditAction.UPDATE,
                actorUsername,
                terminal.getCompanyId(),
                changes.toString()
        ));

        // Блокировка и разблокировка — отдельные действия BLOCK/UNBLOCK: это единственный след
        // массовой правки чужих платёжных ссылок, и число затронутых обязано быть в записи.
        if (statusChange != null) {
            eventPublisher.publishEvent(AuditEvent.of(
                    AuditEntity.TERMINAL,
                    terminal.getId().toString(),
                    request.status() == TerminalStatus.BLOCKED ? AuditAction.BLOCK : AuditAction.UNBLOCK,
                    actorUsername,
                    terminal.getCompanyId(),
                    statusChange
            ));
        }

        return mapToResponse(terminal);
    }

    // Р-39, Р-40: смена ACTIVE/BLOCKED тянет платёжные ссылки в этой же транзакции —
    // заблокированного терминала с оплачиваемыми ссылками не должно быть ни мгновения, а сбой на
    // ссылках обязан откатить и саму блокировку. Возвращает описание с числами — оно идёт в журнал.
    private String applyStatusChange(Terminal terminal, TerminalStatus target) {
        Integer terminalId = terminal.getId();
        terminal.setStatus(target);

        if (target == TerminalStatus.BLOCKED) {
            int suspended = paymentLinkStatusRepository.suspendActiveLinks(terminalId);
            log.info("Blocked terminal {}: suspended {} active payment links", terminalId, suspended);
            return "Blocked terminal " + terminalId + ", suspended " + suspended + " links";
        }

        // Разблокировка делит приостановленные надвое: срок ещё впереди — в ACTIVE, истёк за время
        // блокировки — в EXPIRED, а не в ACTIVE (Р-40).
        Instant now = Instant.now();
        int resumed = paymentLinkStatusRepository.resumeSuspendedLinks(terminalId, now);
        int expired = paymentLinkStatusRepository.expireSuspendedLinks(terminalId, now);
        log.info("Unblocked terminal {}: reactivated {} payment links, expired {}", terminalId, resumed, expired);
        return "Unblocked terminal " + terminalId + ", resumed " + resumed + " links, expired " + expired + " links";
    }

    private void validateReadAccessToCompany(String targetCompanyId, UserPrincipal principal, String entityId) {
        Role actorRole = UserPrincipal.getRole(principal);
        String actorCompanyId = UserPrincipal.getCompanyId(principal);
        if (actorRole == Role.SYSTEM_ADMIN || actorRole == Role.AUDITOR) {
            return;
        }
        if (targetCompanyId != null && targetCompanyId.equals(actorCompanyId)) {
            return;
        }
        // Подшивается под компанию актора, а не названную в запросе (AuditLogService.logDenied).
        auditLogService.logDenied(AuditEntity.TERMINAL, entityId, AuditAction.READ,
                UserPrincipal.getUsername(principal), actorCompanyId,
                "Denied: role " + UserPrincipal.getRawRole(principal) + " of company " + actorCompanyId
                        + " attempted to read terminal " + entityId + " of company " + targetCompanyId);
        throw new InvalidStateException("Access denied");
    }

    private void validateWriteAccessToCompany(String targetCompanyId, UserPrincipal principal,
                                              String entityId, String action, String attempt) {
        Role actorRole = UserPrincipal.getRole(principal);
        String actorCompanyId = UserPrincipal.getCompanyId(principal);
        // Роль проверяется до companyId (P1-15): совпадение компании прав на запись не даёт, иначе
        // COMPANY_EMPLOYEE и любая нераспознанная роль правят терминалы своей компании.
        boolean allowed;
        if (actorRole == Role.AUDITOR || actorRole == null || !TERMINAL_WRITE_ROLES.contains(actorRole)) {
            allowed = false;
        } else if (actorRole == Role.SYSTEM_ADMIN) {
            allowed = true;
        } else {
            allowed = targetCompanyId != null && targetCompanyId.equals(actorCompanyId);
        }
        if (allowed) {
            return;
        }
        auditLogService.logDenied(AuditEntity.TERMINAL, entityId, action,
                UserPrincipal.getUsername(principal), actorCompanyId,
                "Denied: role " + UserPrincipal.getRawRole(principal) + " of company " + actorCompanyId
                        + " attempted to " + attempt);
        if (actorRole == Role.AUDITOR) {
            throw new InvalidStateException("Access denied: AUDITOR is read-only");
        }
        throw new InvalidStateException("Access denied");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private TerminalResponse mapToResponse(Terminal terminal) {
        return new TerminalResponse(
                terminal.getId(),
                terminal.getName(),
                terminal.getLogin(),
                "********",
                terminal.getCompanyId(),
                terminal.getStatus(),
                terminal.getCreatedBy(),
                terminal.getCreatedAt() != null ? terminal.getCreatedAt() : java.time.Instant.now(),
                terminal.getUpdatedBy(),
                terminal.getUpdatedAt() != null ? terminal.getUpdatedAt() : java.time.Instant.now()
        );
    }
}
