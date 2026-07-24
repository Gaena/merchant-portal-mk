package az.millikart.directory.service;

import az.millikart.common.exception.BusinessException;
import az.millikart.common.exception.InvalidStateException;
import az.millikart.directory.domain.Terminal;
import az.millikart.directory.dto.CreateTerminalRequest;
import az.millikart.directory.dto.TerminalResponse;
import az.millikart.directory.dto.UpdateTerminalRequest;
import az.millikart.directory.repository.CompanyRepository;
import az.millikart.directory.repository.TerminalRepository;
import az.millikart.common.security.UserPrincipal;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TerminalService {

    private static final Logger log = LoggerFactory.getLogger(TerminalService.class);

    private final TerminalRepository terminalRepository;
    private final CompanyRepository companyRepository;
    private final AuditLogService auditLogService;

    public TerminalService(TerminalRepository terminalRepository,
                           CompanyRepository companyRepository,
                           AuditLogService auditLogService) {
        this.terminalRepository = terminalRepository;
        this.companyRepository = companyRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public TerminalResponse createTerminal(CreateTerminalRequest request, UserPrincipal principal) {
        String actorUsername = UserPrincipal.getUsername(principal);
        String actorRole = UserPrincipal.getRole(principal);
        String actorCompanyId = UserPrincipal.getCompanyId(principal);

        log.info("Request to create terminal: id={}, name={}, companyId={} by actor: {}", 
                request.id(), request.name(), request.companyId(), actorUsername);

        validateWriteAccessToCompany(request.companyId(), actorRole, actorCompanyId);

        if (!companyRepository.existsById(request.companyId())) {
            throw new BusinessException("Company with ID '" + request.companyId() + "' not found");
        }

        if (terminalRepository.existsById(request.id())) {
            throw new BusinessException("Terminal with ID " + request.id() + " already exists");
        }

        Terminal terminal = Terminal.builder()
                .id(request.id())
                .name(request.name())
                .login(request.login())
                .password(request.password())
                .companyId(request.companyId())
                .createdBy(actorUsername)
                .updatedBy(actorUsername)
                .build();

        terminal = terminalRepository.save(terminal);

        // Audit log
        auditLogService.logAction(
                "TERMINAL",
                terminal.getId().toString(),
                "CREATE",
                actorUsername,
                terminal.getCompanyId(),
                "Created terminal: " + terminal.getName() + " for company " + terminal.getCompanyId()
        );

        return mapToResponse(terminal);
    }

    @Transactional(readOnly = true)
    public List<TerminalResponse> listTerminals(UserPrincipal principal) {
        String actorRole = UserPrincipal.getRole(principal);
        String actorCompanyId = UserPrincipal.getCompanyId(principal);

        List<Terminal> terminals;
        if ("SYSTEM_ADMIN".equals(actorRole) || "AUDITOR".equals(actorRole)) {
            terminals = terminalRepository.findAll();
        } else if ("COMPANY_HEAD".equals(actorRole) || "COMPANY_MANAGER".equals(actorRole) || "COMPANY_EMPLOYEE".equals(actorRole)) {
            if (actorCompanyId == null) {
                throw new InvalidStateException("Access denied: User not assigned to a company");
            }
            terminals = terminalRepository.findAllByCompanyId(actorCompanyId);
        } else {
            throw new InvalidStateException("Access denied");
        }

        return terminals.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "terminals", key = "#id")
    public TerminalResponse getTerminal(Integer id, UserPrincipal principal) {
        String actorRole = UserPrincipal.getRole(principal);
        String actorCompanyId = UserPrincipal.getCompanyId(principal);

        Terminal terminal = terminalRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Terminal not found"));

        validateReadAccessToCompany(terminal.getCompanyId(), actorRole, actorCompanyId);
        return mapToResponse(terminal);
    }

    @Transactional
    @CacheEvict(value = "terminals", key = "#id")
    public TerminalResponse updateTerminal(Integer id, UpdateTerminalRequest request, UserPrincipal principal) {
        String actorUsername = UserPrincipal.getUsername(principal);
        String actorRole = UserPrincipal.getRole(principal);
        String actorCompanyId = UserPrincipal.getCompanyId(principal);

        Terminal terminal = terminalRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Terminal not found"));

        validateWriteAccessToCompany(terminal.getCompanyId(), actorRole, actorCompanyId);

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
            changes.append("Password updated. ");
            terminal.setPassword(request.password());
        }
        if (request.companyId() != null && !request.companyId().isBlank()) {
            validateWriteAccessToCompany(request.companyId(), actorRole, actorCompanyId);
            if (!companyRepository.existsById(request.companyId())) {
                throw new BusinessException("Company with ID '" + request.companyId() + "' not found");
            }
            changes.append("CompanyId changed from '").append(terminal.getCompanyId()).append("' to '").append(request.companyId()).append("'. ");
            terminal.setCompanyId(request.companyId());
        }

        terminal.setUpdatedBy(actorUsername);
        terminal = terminalRepository.save(terminal);

        // Audit log
        auditLogService.logAction(
                "TERMINAL",
                terminal.getId().toString(),
                "UPDATE",
                actorUsername,
                terminal.getCompanyId(),
                changes.toString()
        );

        return mapToResponse(terminal);
    }

    @Transactional
    @CacheEvict(value = "terminals", key = "#id")
    public void deleteTerminal(Integer id, UserPrincipal principal) {
        String actorUsername = UserPrincipal.getUsername(principal);
        String actorRole = UserPrincipal.getRole(principal);
        String actorCompanyId = UserPrincipal.getCompanyId(principal);

        Terminal terminal = terminalRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Terminal not found"));

        validateWriteAccessToCompany(terminal.getCompanyId(), actorRole, actorCompanyId);

        terminalRepository.delete(terminal);

        // Audit log
        auditLogService.logAction(
                "TERMINAL",
                terminal.getId().toString(),
                "DELETE",
                actorUsername,
                terminal.getCompanyId(),
                "Deleted terminal ID " + id
        );
    }

    private void validateReadAccessToCompany(String targetCompanyId, String actorRole, String actorCompanyId) {
        if ("SYSTEM_ADMIN".equals(actorRole) || "AUDITOR".equals(actorRole)) {
            return;
        }
        if (targetCompanyId != null && targetCompanyId.equals(actorCompanyId)) {
            return;
        }
        throw new InvalidStateException("Access denied");
    }

    private void validateWriteAccessToCompany(String targetCompanyId, String actorRole, String actorCompanyId) {
        if ("AUDITOR".equals(actorRole)) {
            throw new InvalidStateException("Access denied: AUDITOR is read-only");
        }
        if ("SYSTEM_ADMIN".equals(actorRole)) {
            return;
        }
        if (targetCompanyId != null && targetCompanyId.equals(actorCompanyId)) {
            return;
        }
        throw new InvalidStateException("Access denied");
    }

    private TerminalResponse mapToResponse(Terminal terminal) {
        return new TerminalResponse(
                terminal.getId(),
                terminal.getName(),
                terminal.getLogin(),
                "********",
                terminal.getCompanyId(),
                terminal.getCreatedBy(),
                terminal.getCreatedAt() != null ? terminal.getCreatedAt() : java.time.Instant.now(),
                terminal.getUpdatedBy(),
                terminal.getUpdatedAt() != null ? terminal.getUpdatedAt() : java.time.Instant.now()
        );
    }
}

