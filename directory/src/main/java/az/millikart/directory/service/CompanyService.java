package az.millikart.directory.service;

import az.millikart.directory.domain.Company;
import az.millikart.directory.dto.CompanyResponse;
import az.millikart.directory.dto.CreateCompanyRequest;
import az.millikart.directory.dto.UpdateCompanyRequest;
import az.millikart.common.exception.BusinessException;
import az.millikart.common.exception.InvalidStateException;
import az.millikart.directory.repository.CompanyRepository;

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
public class CompanyService {

    private static final Logger log = LoggerFactory.getLogger(CompanyService.class);

    private final CompanyRepository companyRepository;
    private final AuditLogService auditLogService;

    public CompanyService(CompanyRepository companyRepository, AuditLogService auditLogService) {
        this.companyRepository = companyRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public CompanyResponse createCompany(CreateCompanyRequest request, UserPrincipal principal) {
        String actorUsername = UserPrincipal.getUsername(principal);
        String actorRole = UserPrincipal.getRole(principal);

        log.info("Request to create company: id={}, name={} by actor: {}", request.id(), request.name(), actorUsername);

        if (!"SYSTEM_ADMIN".equals(actorRole)) {
            throw new InvalidStateException("Access denied: Only SYSTEM_ADMIN can create companies");
        }

        if (companyRepository.existsById(request.id())) {
            throw new BusinessException("Company with ID '" + request.id() + "' already exists");
        }

        Company company = Company.builder()
                .id(request.id())
                .name(request.name())
                .status("ACTIVE")
                .createdBy(actorUsername)
                .updatedBy(actorUsername)
                .build();

        company = companyRepository.save(company);

        // Audit log
        auditLogService.logAction(
                "COMPANY",
                company.getId(),
                "CREATE",
                actorUsername,
                company.getId(),
                "Created company: " + company.getName()
        );

        return mapToResponse(company);
    }

    @Transactional(readOnly = true)
    public List<CompanyResponse> listCompanies(UserPrincipal principal) {
        String actorRole = UserPrincipal.getRole(principal);
        if (!"SYSTEM_ADMIN".equals(actorRole) && !"AUDITOR".equals(actorRole)) {
            throw new InvalidStateException("Access denied: Only SYSTEM_ADMIN or AUDITOR can view all companies");
        }

        return companyRepository.findAll().stream()
                .filter(c -> !"DELETED".equals(c.getStatus()))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "companies", key = "#id")
    public CompanyResponse getCompany(String id, UserPrincipal principal) {
        String actorRole = UserPrincipal.getRole(principal);
        String actorCompanyId = UserPrincipal.getCompanyId(principal);
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Company not found"));

        if ("DELETED".equals(company.getStatus())) {
            throw new BusinessException("Company not found");
        }

        validateAccess(company.getId(), actorRole, actorCompanyId);
        return mapToResponse(company);
    }

    @Transactional
    @CacheEvict(value = "companies", key = "#id")
    public CompanyResponse updateCompany(String id, UpdateCompanyRequest request, UserPrincipal principal) {
        String actorUsername = UserPrincipal.getUsername(principal);
        String actorRole = UserPrincipal.getRole(principal);
        if (!"SYSTEM_ADMIN".equals(actorRole)) {
            throw new InvalidStateException("Access denied: Only SYSTEM_ADMIN can update companies");
        }

        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Company not found"));

        if ("DELETED".equals(company.getStatus())) {
            throw new BusinessException("Company not found");
        }

        StringBuilder changes = new StringBuilder();
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

        // Audit log
        auditLogService.logAction(
                "COMPANY",
                company.getId(),
                "UPDATE",
                actorUsername,
                company.getId(),
                changes.toString()
        );

        return mapToResponse(company);
    }

    @Transactional
    @CacheEvict(value = "companies", key = "#id")
    public void deleteCompany(String id, UserPrincipal principal) {
        String actorUsername = UserPrincipal.getUsername(principal);
        String actorRole = UserPrincipal.getRole(principal);
        if (!"SYSTEM_ADMIN".equals(actorRole)) {
            throw new InvalidStateException("Access denied: Only SYSTEM_ADMIN can delete companies");
        }

        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Company not found"));

        company.setStatus("DELETED");
        company.setUpdatedBy(actorUsername);
        companyRepository.save(company);

        // Audit log
        auditLogService.logAction(
                "COMPANY",
                company.getId(),
                "DELETE",
                actorUsername,
                company.getId(),
                "Soft deleted company"
        );
    }

    private void validateAccess(String targetCompanyId, String actorRole, String actorCompanyId) {
        if ("SYSTEM_ADMIN".equals(actorRole) || "AUDITOR".equals(actorRole)) {
            return;
        }
        if (targetCompanyId != null && targetCompanyId.equals(actorCompanyId)) {
            return;
        }
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
