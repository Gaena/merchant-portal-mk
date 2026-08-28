package az.millikart.directory.service;

import az.millikart.directory.domain.Company;
import az.millikart.directory.dto.CompanyResponse;
import az.millikart.directory.dto.CreateCompanyRequest;
import az.millikart.directory.dto.UpdateCompanyRequest;
import az.millikart.common.dto.PagedResponse;
import az.millikart.common.exception.BusinessException;
import az.millikart.common.exception.InvalidStateException;
import az.millikart.directory.repository.CompanyRepository;

import az.millikart.common.audit.AuditAction;
import az.millikart.common.audit.AuditEntity;
import az.millikart.common.audit.AuditEvent;
import az.millikart.common.audit.AuditLogService;
import az.millikart.common.search.SearchTerms;
import az.millikart.common.security.Role;
import az.millikart.common.security.UserPrincipal;
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
public class CompanyService {

    private static final Logger log = LoggerFactory.getLogger(CompanyService.class);

    private static final String STATUS_ACTIVE = "ACTIVE";

    // Маркер мягкого удаления: такая компания невидима на всех путях чтения.
    private static final String STATUS_DELETED = "DELETED";

    private final CompanyRepository companyRepository;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;

    public CompanyService(CompanyRepository companyRepository,
                          AuditLogService auditLogService,
                          ApplicationEventPublisher eventPublisher) {
        this.companyRepository = companyRepository;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public CompanyResponse createCompany(CreateCompanyRequest request, UserPrincipal principal) {
        String actorUsername = UserPrincipal.getUsername(principal);
        Role actorRole = UserPrincipal.getRole(principal);

        log.info("Request to create company: id={}, name={} by actor: {}", request.id(), request.name(), actorUsername);

        if (actorRole != Role.SYSTEM_ADMIN) {
            // Подшивается под компанию актора, не названную в запросе (AuditLogService.logDenied).
            auditLogService.logDenied(AuditEntity.COMPANY, request.id(), AuditAction.CREATE, actorUsername,
                    UserPrincipal.getCompanyId(principal),
                    "Denied: role " + UserPrincipal.getRawRole(principal)
                            + " attempted to create company '" + request.name() + "'");
            throw new InvalidStateException("Access denied: Only SYSTEM_ADMIN can create companies");
        }

        if (companyRepository.existsById(request.id())) {
            throw new BusinessException("Company with ID '" + request.id() + "' already exists");
        }

        Company company = Company.builder()
                .id(request.id())
                .name(request.name())
                .status(STATUS_ACTIVE)
                .createdBy(actorUsername)
                .updatedBy(actorUsername)
                .build();

        company = companyRepository.save(company);

        // Пишется AuditLogWriter после коммита этой транзакции (Р-35).
        eventPublisher.publishEvent(AuditEvent.of(
                AuditEntity.COMPANY,
                company.getId(),
                AuditAction.CREATE,
                actorUsername,
                company.getId(),
                "Created company: " + company.getName()
        ));

        return mapToResponse(company);
    }

    // Страница компаний (P2-1) с поиском по name и id (P3-1). Страницы, фильтр мягкого удаления,
    // поиск и порядок — работа базы, не памяти. Сортировка name + id: имена компаний не уникальны,
    // а без уникального довеска база вправе упорядочить одинаковые имена по-разному между двумя
    // запросами страниц, и компания попадёт то на обе соседние страницы, то ни на одну.
    @Transactional(readOnly = true)
    public PagedResponse<CompanyResponse> listCompanies(Pageable pageable, UserPrincipal principal,
                                                        String search) {
        Role actorRole = UserPrincipal.getRole(principal);
        if (actorRole != Role.SYSTEM_ADMIN && actorRole != Role.AUDITOR) {
            auditLogService.logDenied(AuditEntity.COMPANY, "ALL", AuditAction.LIST,
                    UserPrincipal.getUsername(principal), UserPrincipal.getCompanyId(principal),
                    "Denied: role " + UserPrincipal.getRawRole(principal)
                            + " attempted to list all companies");
            throw new InvalidStateException("Access denied: Only SYSTEM_ADMIN or AUDITOR can view all companies");
        }

        Pageable byName = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
        Page<Company> page = companyRepository.search(STATUS_DELETED,
                SearchTerms.toLikePattern(search), byName);

        return PagedResponse.of(page, page.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList()));
    }

    @Transactional(readOnly = true)
    public CompanyResponse getCompany(String id, UserPrincipal principal) {
        Role actorRole = UserPrincipal.getRole(principal);
        String actorCompanyId = UserPrincipal.getCompanyId(principal);
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Company not found"));

        if (STATUS_DELETED.equals(company.getStatus())) {
            throw new BusinessException("Company not found");
        }

        validateAccess(company.getId(), principal, actorRole, actorCompanyId);
        return mapToResponse(company);
    }

    @Transactional
    public CompanyResponse updateCompany(String id, UpdateCompanyRequest request, UserPrincipal principal) {
        String actorUsername = UserPrincipal.getUsername(principal);
        Role actorRole = UserPrincipal.getRole(principal);
        if (actorRole != Role.SYSTEM_ADMIN) {
            auditLogService.logDenied(AuditEntity.COMPANY, id, AuditAction.UPDATE, actorUsername,
                    UserPrincipal.getCompanyId(principal),
                    "Denied: role " + UserPrincipal.getRawRole(principal)
                            + " attempted to update company " + id);
            throw new InvalidStateException("Access denied: Only SYSTEM_ADMIN can update companies");
        }

        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Company not found"));

        if (STATUS_DELETED.equals(company.getStatus())) {
            throw new BusinessException("Company not found");
        }

        StringBuilder changes = new StringBuilder();
        String previousStatus = company.getStatus();
        if (request.name() != null && !request.name().isBlank()) {
            changes.append("Name changed from '").append(company.getName()).append("' to '").append(request.name()).append("'. ");
            company.setName(request.name());
        }
        if (request.status() != null && !request.status().isBlank()) {
            changes.append("Status changed from '").append(company.getStatus()).append("' to '").append(request.status()).append("'. ");
            company.setStatus(request.status());
        }

        company.setUpdatedBy(actorUsername);
        company = companyRepository.save(company);

        // Пишется AuditLogWriter после коммита этой транзакции (Р-35).
        eventPublisher.publishEvent(AuditEvent.of(
                AuditEntity.COMPANY,
                company.getId(),
                AuditAction.UPDATE,
                actorUsername,
                company.getId(),
                changes.toString()
        ));

        // Смена статуса — отдельное событие, как у пользователей и терминалов (P3-2): ревизор ищет
        // блокировки по действию, а не вычитывая прозу каждого UPDATE.
        if (request.status() != null && !request.status().isBlank()
                && !request.status().equals(previousStatus)) {
            boolean reactivated = STATUS_ACTIVE.equals(request.status());
            eventPublisher.publishEvent(AuditEvent.of(
                    AuditEntity.COMPANY,
                    company.getId(),
                    reactivated ? AuditAction.UNBLOCK : AuditAction.BLOCK,
                    actorUsername,
                    company.getId(),
                    (reactivated ? "Company reactivated" : "Company set to " + request.status())
                            + " (was " + previousStatus + ")"
            ));
        }

        return mapToResponse(company);
    }

    @Transactional
    public void deleteCompany(String id, UserPrincipal principal) {
        String actorUsername = UserPrincipal.getUsername(principal);
        Role actorRole = UserPrincipal.getRole(principal);
        if (actorRole != Role.SYSTEM_ADMIN) {
            auditLogService.logDenied(AuditEntity.COMPANY, id, AuditAction.DELETE, actorUsername,
                    UserPrincipal.getCompanyId(principal),
                    "Denied: role " + UserPrincipal.getRawRole(principal)
                            + " attempted to delete company " + id);
            throw new InvalidStateException("Access denied: Only SYSTEM_ADMIN can delete companies");
        }

        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Company not found"));

        company.setStatus(STATUS_DELETED);
        company.setUpdatedBy(actorUsername);
        companyRepository.save(company);

        // Пишется AuditLogWriter после коммита этой транзакции (Р-35).
        eventPublisher.publishEvent(AuditEvent.of(
                AuditEntity.COMPANY,
                company.getId(),
                AuditAction.DELETE,
                actorUsername,
                company.getId(),
                "Soft deleted company"
        ));
    }

    private void validateAccess(String targetCompanyId, UserPrincipal principal,
                                Role actorRole, String actorCompanyId) {
        if (actorRole == Role.SYSTEM_ADMIN || actorRole == Role.AUDITOR) {
            return;
        }
        if (targetCompanyId != null && targetCompanyId.equals(actorCompanyId)) {
            return;
        }
        auditLogService.logDenied(AuditEntity.COMPANY, targetCompanyId, AuditAction.READ,
                UserPrincipal.getUsername(principal), actorCompanyId,
                "Denied: role " + UserPrincipal.getRawRole(principal) + " of company " + actorCompanyId
                        + " attempted to read company " + targetCompanyId);
        throw new InvalidStateException("Access denied");
    }

    private CompanyResponse mapToResponse(Company company) {
        return new CompanyResponse(
                company.getId(),
                company.getName(),
                company.getStatus(),
                company.getCreatedBy(),
                company.getCreatedAt() != null ? company.getCreatedAt() : java.time.Instant.now(),
                company.getUpdatedBy(),
                company.getUpdatedAt() != null ? company.getUpdatedAt() : java.time.Instant.now()
        );
    }
}
