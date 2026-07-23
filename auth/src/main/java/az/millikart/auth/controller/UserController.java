package az.millikart.auth.controller;

import az.millikart.auth.dto.CreateUserRequest;
import az.millikart.auth.dto.UpdateUserRequest;
import az.millikart.auth.dto.UserResponse;
import az.millikart.auth.service.UserService;
import az.millikart.common.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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

    @GetMapping
    public List<UserResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return userService.listUsers(principal);
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
