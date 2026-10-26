package com.clydehomu.disputes.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.clydehomu.disputes.auth.domain.AppUser;
import com.clydehomu.disputes.auth.domain.Role;
import com.clydehomu.disputes.auth.infrastructure.AppUserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class AppUserDetailsServiceTest {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final AppUserDetailsService service = new AppUserDetailsService(users);

    @Test
    void mapsAStoredCustomerToAnAuthenticatedUserWithItsRoleAuthority() {
        AppUser stored = new AppUser("thandi", "hash", "Thandi Mokoena", Role.CUSTOMER, Instant.now());
        when(users.findByUsername("thandi")).thenReturn(Optional.of(stored));

        UserDetails details = service.loadUserByUsername("thandi");

        assertThat(details).isInstanceOf(AuthenticatedUser.class);
        AuthenticatedUser user = (AuthenticatedUser) details;
        assertThat(user.getUsername()).isEqualTo("thandi");
        assertThat(user.getPassword()).isEqualTo("hash");
        assertThat(user.getDisplayName()).isEqualTo("Thandi Mokoena");
        assertThat(user.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(user.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_CUSTOMER");
    }

    @Test
    void anAgentGetsTheAgentAuthority() {
        AppUser stored = new AppUser("lerato", "hash", "Lerato Dlamini", Role.AGENT, Instant.now());
        when(users.findByUsername("lerato")).thenReturn(Optional.of(stored));

        assertThat(service.loadUserByUsername("lerato").getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_AGENT");
    }

    @Test
    void theHashIsErasedWhenSpringSecurityClearsCredentials() {
        AppUser stored = new AppUser("thandi", "hash", "Thandi Mokoena", Role.CUSTOMER, Instant.now());
        when(users.findByUsername("thandi")).thenReturn(Optional.of(stored));
        AuthenticatedUser user = (AuthenticatedUser) service.loadUserByUsername("thandi");

        user.eraseCredentials();

        assertThat(user.getPassword()).isNull();
    }

    @Test
    void anUnknownUsernameIsReportedAsNotFound() {
        when(users.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("nobody")).isInstanceOf(UsernameNotFoundException.class);
    }
}
