package az.millikart.auth.controller;

import az.millikart.auth.dto.CreateUserRequest;
import az.millikart.auth.dto.UpdateUserRequest;
import az.millikart.auth.dto.UserResponse;
import az.millikart.auth.service.UserService;
import az.millikart.common.dto.PagedResponse;
import az.millikart.common.search.SearchTerms;
import az.millikart.common.security.Role;
import az.millikart.common.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.Locale;
import java.util.UUID;
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
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request,
                               @AuthenticationPrincipal UserPrincipal principal) {
        return userService.createUser(request, principal);
    }

    // Потолок страницы списка аккаунтов — тот же, что у журнала аудита, чтобы все постраничные
    // списки проекта вели себя одинаково.
    private static final int MAX_PAGE_SIZE = 200;

    // page/size приводятся к границам, а не идут как есть: PageRequest.of бросает при page < 0
    // или size < 1, и IllegalArgumentException из опечатки в query-параметре вернётся как 500.
    // search и role (P3-1) правятся так же: пустой search — «без поиска», неизвестная роль — «без
    // фильтра»: разошедшийся с бэкендом фильтр обязан деградировать к полному списку, а не к 400.
    @GetMapping
    public PagedResponse<UserResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @AuthenticationPrincipal UserPrincipal principal) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE));
        return userService.listUsers(pageable, principal,
                SearchTerms.normalize(search), normalizeRole(role));
    }

    // Trim и upper-case; значение, которого Role не знает, схлопывается в «без фильтра».
    private static String normalizeRole(String role) {
        if (role == null) {
            return null;
        }
        String cleaned = role.trim().toUpperCase(Locale.ROOT);
        return Role.fromValue(cleaned).isPresent() ? cleaned : null;
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable UUID id,
                            @AuthenticationPrincipal UserPrincipal principal) {
        return userService.getUser(id, principal);
    }

    @PatchMapping("/{id}")
    public UserResponse update(@PathVariable UUID id,
                               @Valid @RequestBody UpdateUserRequest request,
                               @AuthenticationPrincipal UserPrincipal principal) {
        return userService.updateUser(id, request, principal);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id,
                       @AuthenticationPrincipal UserPrincipal principal) {
        userService.deleteUser(id, principal);
    }
}
