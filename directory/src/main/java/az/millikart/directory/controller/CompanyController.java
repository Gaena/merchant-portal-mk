package az.millikart.directory.controller;

import az.millikart.common.security.UserPrincipal;
import az.millikart.directory.dto.CompanyResponse;
import az.millikart.directory.dto.CreateCompanyRequest;
import az.millikart.directory.dto.UpdateCompanyRequest;
import az.millikart.directory.service.CompanyService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @GetMapping
    public List<CompanyResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return companyService.listCompanies(principal);
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
