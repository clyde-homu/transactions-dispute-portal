package com.clydehomu.disputes.auth.application;

import com.clydehomu.disputes.auth.domain.AppUser;
import com.clydehomu.disputes.auth.domain.Role;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * The signed-in user as Spring Security sees it. It carries the id and display name so controllers can answer
 * "who am I" from the session without a database round trip. The password hash is erased after authentication.
 */
public final class AuthenticatedUser extends User {

    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String displayName;
    private final Role role;

    public AuthenticatedUser(AppUser user) {
        super(
                user.getUsername(),
                user.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
        this.id = user.getId();
        this.displayName = user.getDisplayName();
        this.role = user.getRole();
    }

    public UUID getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Role getRole() {
        return role;
    }
}
