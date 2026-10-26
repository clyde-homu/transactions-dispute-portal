package com.clydehomu.disputes.auth.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

/**
 * Starts and ends the HTTP session around a JSON login. Spring Security does this itself for form logins; with a JSON
 * endpoint the same steps have to be done by hand: new session id, saved security context, fresh CSRF token.
 */
@Component
public class SessionAuthenticator {

    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();
    private final SessionAuthenticationStrategy fixationProtection = new ChangeSessionIdAuthenticationStrategy();
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public SessionAuthenticator(
            SecurityContextRepository securityContextRepository, CsrfTokenRepository csrfTokenRepository) {
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    public void start(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        // An id issued before login must not stay valid after it.
        fixationProtection.onAuthentication(authentication, request, response);
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        rotateCsrfToken(request, response);
    }

    /** Safe to call without a session: there is simply nothing to invalidate. */
    public void end(HttpServletRequest request, HttpServletResponse response) {
        logoutHandler.logout(request, response, contextHolder.getContext().getAuthentication());
        rotateCsrfToken(request, response);
    }

    private void rotateCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(request), request, response);
    }
}
