package az.millikart.directory.controller;

import az.millikart.common.dto.PagedResponse;
import az.millikart.common.search.SearchTerms;
import az.millikart.common.security.UserPrincipal;
import az.millikart.directory.dto.CompanyResponse;
import az.millikart.directory.dto.CreateCompanyRequest;
import az.millikart.directory.dto.UpdateCompanyRequest;
import az.millikart.directory.service.CompanyService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse create(@Valid @RequestBody CreateCompanyRequest request,
                                  @AuthenticationPrincipal UserPrincipal principal) {
        return companyService.createCompany(request, principal);
    }

    // Потолок, общий для всех постраничных списков проекта — см. AuditLogController.
    private static final int MAX_PAGE_SIZE = 200;

    // Значения зажимаются, а не передаются как есть: PageRequest.of бросает на page < 0 и size < 1,
    // и это уходит клиенту как 500 со стектрейсом в логе. search (P3-1) приводится так же: пусто —
    // «нет поиска», слишком длинное обрезается.
    @GetMapping
    public PagedResponse<CompanyResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal UserPrincipal principal) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return companyService.listCompanies(pageable, principal, SearchTerms.normalize(search));
    }

    @GetMapping("/{id}")
    public CompanyResponse get(@PathVariable String id,
                               @AuthenticationPrincipal UserPrincipal principal) {
        return companyService.getCompany(id, principal);
    }

    @PatchMapping("/{id}")
    public CompanyResponse update(@PathVariable String id,
                                  @Valid @RequestBody UpdateCompanyRequest request,
                                  @AuthenticationPrincipal UserPrincipal principal) {
        return companyService.updateCompany(id, request, principal);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id,
                       @AuthenticationPrincipal UserPrincipal principal) {
        companyService.deleteCompany(id, principal);
    }
}
