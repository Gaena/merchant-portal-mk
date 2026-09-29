package az.millikart.directory.service;

import az.millikart.common.dto.PagedResponse;
import az.millikart.common.exception.BusinessException;
import az.millikart.common.exception.InvalidStateException;
import az.millikart.directory.domain.Company;
import az.millikart.directory.domain.Terminal;
import az.millikart.directory.domain.TerminalStatus;
import az.millikart.directory.domain.TerminalStatusSource;
import az.millikart.directory.dto.CreateTerminalRequest;
import az.millikart.directory.dto.ProviderTerminalOption;
import az.millikart.directory.dto.TerminalOptionResponse;
import az.millikart.directory.dto.TerminalResponse;
import az.millikart.directory.dto.UpdateTerminalRequest;
import az.millikart.directory.repository.CompanyRepository;
import az.millikart.directory.repository.PaymentLinkStatusRepository;
import az.millikart.directory.repository.ProviderLoginSnapshotRepository;
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
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
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

    // entityId отказа в заведении: номер терминалу выдаётся только при сохранении (Р-81).
    private static final String NEW_TERMINAL = "NEW";

    private static final String PROVIDER_ACTIVE = "Active";

    private final TerminalRepository terminalRepository;
    private final CompanyRepository companyRepository;
    private final PaymentLinkStatusRepository paymentLinkStatusRepository;
    private final ProviderTerminalStatusRepository providerTerminals;
    private final ProviderLoginSnapshotRepository providerLogins;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;

    public TerminalService(TerminalRepository terminalRepository,
                           CompanyRepository companyRepository,
                           PaymentLinkStatusRepository paymentLinkStatusRepository,
                           ProviderTerminalStatusRepository providerTerminals,
                           ProviderLoginSnapshotRepository providerLogins,
                           AuditLogService auditLogService,
                           ApplicationEventPublisher eventPublisher) {
        this.terminalRepository = terminalRepository;
        this.companyRepository = companyRepository;
        this.paymentLinkStatusRepository = paymentLinkStatusRepository;
        this.providerTerminals = providerTerminals;
        this.providerLogins = providerLogins;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
    }

    // Заводит только SYSTEM_ADMIN и только выбором из справочника провайдера (Р-80, Р-93).
    @Transactional
    public TerminalResponse createTerminal(CreateTerminalRequest request, UserPrincipal principal) {
        String actorUsername = UserPrincipal.getUsername(principal);
        String merchantRid = request.merchantRid().trim();

        log.info("Request to create terminal: merchantRid={}, companyId={}", merchantRid, request.companyId());

        if (UserPrincipal.getRole(principal) != Role.SYSTEM_ADMIN) {
            auditLogService.logDenied(AuditEntity.TERMINAL, NEW_TERMINAL, AuditAction.CREATE, actorUsername,
                    UserPrincipal.getCompanyId(principal), "Denied: role " + UserPrincipal.getRawRole(principal)
                            + " attempted to create a terminal for company " + request.companyId());
            if (UserPrincipal.getRole(principal) == Role.AUDITOR) {
                throw new InvalidStateException("Access denied: AUDITOR is read-only");
            }
            throw new InvalidStateException("Access denied");
        }

        // Удалённая компания — как несуществующая: список компаний её уже не показывает (Р-103).
        Company company = companyRepository.findById(request.companyId())
                .filter(found -> !CompanyService.STATUS_DELETED.equals(found.getStatus()))
                .orElseThrow(() -> new BusinessException("Company with ID '" + request.companyId() + "' not found"));

        // Один терминал провайдера — одна наша компания: общий мерчант двух логинов достаётся
        // первой заведшей (Р-67, Р-96).
        terminalRepository.findByMerchantRid(merchantRid).ifPresent(existing -> {
            throw new BusinessException("Provider terminal " + merchantRid
                    + " is already linked to terminal " + existing.getId());
        });
        // Название и логин — только от провайдера: введённые руками разойдутся с ним (Р-67).
        ProviderTerminalStatusRepository.ProviderTerminalRow row = providerTerminals
                .findByRid(merchantRid)
                .orElseThrow(() -> new BusinessException(
                        "Provider terminal " + merchantRid + " is not in the synchronised list"));
        if (row.title() == null || row.title().isBlank() || row.gatewayLogin() == null
                || row.terminalRid() == null || row.terminalRid().isBlank()) {
            throw new BusinessException("Provider terminal " + merchantRid
                    + " has no name, login or terminal number in the synchronised list");
        }
        // Выключенный у провайдера платежей не примет; форма его и не предлагает — это для прямого API (Р-103).
        if (!row.active()) {
            throw new BusinessException("Provider terminal " + merchantRid + " is not active at the provider");
        }
        if (!merchantsOfCompanyLogin(company).contains(merchantRid)) {
            throw new BusinessException("Provider terminal " + merchantRid
                    + " does not belong to the multimerchant login of company " + company.getId());
        }

        // Номер берётся последним, когда все проверки пройдены: отказ не должен тратить номера.
        Terminal terminal = Terminal.builder()
                .id(Math.toIntExact(terminalRepository.nextId()))
                .name(row.title())
                .login(row.gatewayLogin())
                .terminalRid(row.terminalRid())
                .companyId(request.companyId())
                .merchantRid(merchantRid)
                .createdBy(actorUsername)
                .updatedBy(actorUsername)
                .build();

        terminal = terminalRepository.saveAndFlush(terminal);

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

    // Сортировка кончается id: иначе терминалы с равным именем прыгают между страницами (P2-1).
    // Скоуп компании — условие того же запроса: поиск чужой терминал не находит (P3-1).
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

    // Заблокированные отдаются намеренно, фильтрует потребитель (Р-45): форме ссылки нужны ACTIVE,
    // экранам платежей — все, иначе старый платёж теряет подпись терминала.
    @Transactional(readOnly = true)
    public List<TerminalOptionResponse> listTerminalOptions(UserPrincipal principal) {
        List<Terminal> terminals = isGlobalReader(principal)
                ? terminalRepository.findAllByOrderByNameAscIdAsc()
                : terminalRepository.findAllByCompanyIdOrderByNameAscIdAsc(requireOwnCompany(principal));

        return terminals.stream()
                .map(t -> new TerminalOptionResponse(t.getId(), t.getName(), t.getLogin(), t.getTerminalRid(), t.getStatus()))
                .collect(Collectors.toList());
    }

    // Варианты для формы заведения терминала (Р-96).
    @Transactional(readOnly = true)
    public List<ProviderTerminalOption> listProviderTerminals(String companyId, UserPrincipal principal) {
        if (UserPrincipal.getRole(principal) != Role.SYSTEM_ADMIN) {
            auditLogService.logDenied(AuditEntity.TERMINAL, "ALL", AuditAction.LIST,
                    UserPrincipal.getUsername(principal), UserPrincipal.getCompanyId(principal),
                    "Denied: role " + UserPrincipal.getRawRole(principal) + " attempted to list provider terminals");
            throw new InvalidStateException("Access denied");
        }
        if (companyId == null || companyId.isBlank()) {
            throw new BusinessException("companyId is required");
        }
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new BusinessException("Company with ID '" + companyId + "' not found"));
        Set<String> merchants = merchantsOfCompanyLogin(company);
        Set<String> linked = new HashSet<>(terminalRepository.findAllMerchantRids());
        return providerTerminals.rowsByRid().values().stream()
                .filter(row -> row.active() && merchants.contains(row.rid()) && !linked.contains(row.rid())
                        && row.terminalRid() != null && !row.terminalRid().isBlank())
                .sorted(Comparator.comparing((ProviderTerminalStatusRepository.ProviderTerminalRow row) ->
                        row.title() != null ? row.title() : "").thenComparing(ProviderTerminalStatusRepository.ProviderTerminalRow::rid))
                .map(row -> new ProviderTerminalOption(row.rid(), row.title(), row.login(), row.terminalRid()))
                .toList();
    }

    // Только мерчанты логина компании: иначе она ходила бы к провайдеру своими кредами за чужого
    // мерчанта (Р-96).
    private Set<String> merchantsOfCompanyLogin(Company company) {
        String providerLogin = company.getProviderLogin();
        if (providerLogin == null || !providerLogin.startsWith(CompanyService.MULTI_MERCHANT_PREFIX)) {
            return Set.of();
        }
        return providerLogins.linksOf(providerLogin.substring(CompanyService.MULTI_MERCHANT_PREFIX.length())).stream()
                .filter(link -> PROVIDER_ACTIVE.equals(link.loginStatus()) && PROVIDER_ACTIVE.equals(link.linkStatus())
                        && link.merchantRid() != null)
                .map(ProviderLoginSnapshotRepository.LoginLink::merchantRid)
                .collect(Collectors.toSet());
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
        if (request.companyId() != null && !request.companyId().isBlank()) {
            validateWriteAccessToCompany(request.companyId(), principal,
                    String.valueOf(id), AuditAction.UPDATE,
                    "move terminal " + id + " to company " + request.companyId());
            Company target = companyRepository.findById(request.companyId())
                    .orElseThrow(() -> new BusinessException("Company with ID '" + request.companyId() + "' not found"));
            // Как при заведении: иначе ссылки ушли бы к провайдеру с кредами компании, чей логин
            // этого мерчанта не знает, а выписка его платежей осталась бы у прежней (Р-96, Р-97).
            if (!target.getId().equals(terminal.getCompanyId())
                    && (terminal.getMerchantRid() == null || !merchantsOfCompanyLogin(target).contains(terminal.getMerchantRid()))) {
                throw new BusinessException("Terminal " + id + " cannot be moved to company " + target.getId()
                        + ": its provider merchant is not linked to the multimerchant login of that company");
            }
            changes.append("CompanyId changed from '").append(terminal.getCompanyId()).append("' to '").append(request.companyId()).append("'. ");
            terminal.setCompanyId(request.companyId());
        }

        // Статус — последним: только его правка трогает ссылки. Тот же статус — не изменение, иначе
        // PATCH с объектом целиком переприостанавливал бы ссылки на каждом сохранении.
        String statusChange = null;
        if (request.status() != null && request.status() != terminal.getStatus()) {
            // Выключенный синхронизацией включает только она (Р-66): у провайдера он снят с
            // обслуживания, и включение подняло бы ссылки, по которым платёж всё равно не пройдёт.
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
            // Ручную блокировку сверка не снимает (Р-66).
            terminal.setStatusSource(TerminalStatusSource.MANUAL);
            changes.append(statusChange).append(". ");
        }

        terminal.setUpdatedBy(actorUsername);
        terminal = terminalRepository.saveAndFlush(terminal);

        eventPublisher.publishEvent(AuditEvent.of(
                AuditEntity.TERMINAL,
                terminal.getId().toString(),
                AuditAction.UPDATE,
                actorUsername,
                terminal.getCompanyId(),
                changes.toString()
        ));

        // Отдельная запись BLOCK/UNBLOCK — единственный след массовой правки платёжных ссылок,
        // число затронутых обязано быть в ней.
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

    // Ссылки меняются в той же транзакции: сбой на них откатывает и блокировку, а заблокированного
    // терминала с оплачиваемыми ссылками не бывает ни мгновения (Р-39, Р-40).
    private String applyStatusChange(Terminal terminal, TerminalStatus target) {
        Integer terminalId = terminal.getId();
        terminal.setStatus(target);

        if (target == TerminalStatus.BLOCKED) {
            int suspended = paymentLinkStatusRepository.suspendActiveLinks(terminalId);
            log.info("Blocked terminal {}: suspended {} active payment links", terminalId, suspended);
            return "Blocked terminal " + terminalId + ", suspended " + suspended + " links";
        }

        // Истёкшие за время блокировки — в EXPIRED, а не в ACTIVE (Р-40).
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
        // Отказ пишется с компанией актора, а не цели (Р-104).
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
        // Роль проверяется до companyId: иначе COMPANY_EMPLOYEE и нераспознанная роль правили бы
        // терминалы своей компании (P1-15).
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

    private TerminalResponse mapToResponse(Terminal terminal) {
        return new TerminalResponse(
                terminal.getId(),
                terminal.getName(),
                terminal.getLogin(),
                terminal.getTerminalRid(),
                terminal.getCompanyId(),
                terminal.getStatus(),
                terminal.getCreatedBy(),
                terminal.getCreatedAt(),
                terminal.getUpdatedBy(),
                terminal.getUpdatedAt()
        );
    }
}
