package com.clydehomu.disputes.auth.application;

import com.clydehomu.disputes.shared.error.ApiException;
import com.clydehomu.disputes.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

/**
 * Checks credentials and applies the sign-in throttle. Putting the session in place is the web layer's job, so this
 * class never touches the HTTP request.
 */
@Service
public class AuthService {

    // BCrypt ignores everything after 72 bytes, so a longer password could never have been set.
    private static final int MAX_PASSWORD_BYTES = 72;

    private final AuthenticationManager authenticationManager;
    private final LoginAttemptTracker attempts;

    public AuthService(AuthenticationManager authenticationManager, LoginAttemptTracker attempts) {
        this.authenticationManager = authenticationManager;
        this.attempts = attempts;
    }

    /**
     * Returns the authenticated token, or throws BAD_CREDENTIALS (the same error for an unknown user and a wrong
     * password) or TOO_MANY_LOGIN_ATTEMPTS.
     */
    public Authentication login(String username, String password) {
        if (attempts.isBlocked(username)) {
            throw new ApiException(
                    ErrorCode.TOO_MANY_LOGIN_ATTEMPTS, "Too many failed sign-in attempts. Try again in a few minutes.");
        }
        Authentication authenticated;
        try {
            authenticated = authenticate(username, password);
        } catch (AuthenticationException failure) {
            attempts.recordFailure(username);
            throw new ApiException(ErrorCode.BAD_CREDENTIALS, "The username or password is incorrect.");
        }
        attempts.reset(username);
        return authenticated;
    }

    private Authentication authenticate(String username, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new BadCredentialsException("Password longer than BCrypt can hold");
        }
        return authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username, password));
    }
}
