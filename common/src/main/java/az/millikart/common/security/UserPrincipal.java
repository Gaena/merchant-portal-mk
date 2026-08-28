package az.millikart.common.security;

import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class UserPrincipal implements UserDetails, Principal {

    private final String userId;
    private final String username;
    private final String rawRole;
    private final Role role;
    private final String companyId;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(String userId, String username, String role, String companyId) {
        this.userId = userId;
        this.username = username;
        this.rawRole = role;
        this.role = Role.fromValue(role).orElse(null);
        this.companyId = companyId;
        if (role != null && !role.isBlank()) {
            String roleName = role.startsWith("ROLE_") ? role : "ROLE_" + role;
            this.authorities = Collections.singletonList(new SimpleGrantedAuthority(roleName));
        } else {
            this.authorities = Collections.emptyList();
        }
    }

    public String getUserId() {
        return userId;
    }

    // Роль ровно как пришла в токене — только для логов и сообщений, не для решений о доступе.
    public String getRawRole() {
        return rawRole;
    }

    // null означает, что токен принёс нераспознанное значение: вызывающий обязан отказать (P0-4).
    public Role getRole() {
        return role;
    }

    public String getCompanyId() {
        return companyId;
    }

    @Override
    public String getName() {
        return username != null ? username : userId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public static String getUserId(UserPrincipal principal) {
        return principal != null ? principal.getUserId() : null;
    }

    public static String getUsername(UserPrincipal principal) {
        return principal != null ? principal.getUsername() : "system";
    }

    // null — либо principal'а нет, либо его роль нераспознана; в обоих случаях доступ закрыт.
    public static Role getRole(UserPrincipal principal) {
        return principal != null ? principal.getRole() : null;
    }

    public static String getRawRole(UserPrincipal principal) {
        return principal != null ? principal.getRawRole() : null;
    }

    public static String getCompanyId(UserPrincipal principal) {
        return principal != null ? principal.getCompanyId() : null;
    }
}
