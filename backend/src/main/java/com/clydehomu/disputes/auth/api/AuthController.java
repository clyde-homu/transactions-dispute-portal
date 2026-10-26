package com.clydehomu.disputes.auth.api;

import com.clydehomu.disputes.auth.application.AuthService;
import com.clydehomu.disputes.auth.application.AuthenticatedUser;
import com.clydehomu.disputes.auth.infrastructure.SessionAuthenticator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final SessionAuthenticator sessionAuthenticator;

    public AuthController(AuthService authService, SessionAuthenticator sessionAuthenticator) {
        this.authService = authService;
        this.sessionAuthenticator = sessionAuthenticator;
    }

    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        // The token is created lazily; reading it is what makes Spring write the XSRF-TOKEN cookie.
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public MeResponse login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        Authentication authentication = authService.login(request.username(), request.password());
        sessionAuthenticator.start(authentication, httpRequest, response);
        return MeResponse.from((AuthenticatedUser) authentication.getPrincipal());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        sessionAuthenticator.end(request, response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return MeResponse.from(user);
    }
}
