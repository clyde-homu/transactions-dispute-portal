package com.clydehomu.disputes.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = SecurityConfigTest.ProbeController.class)
@Import({SecurityConfig.class, SecurityConfigTest.ProbeController.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousApiCallGetsAProblemJson401() throws Exception {
        mockMvc.perform(get("/api/v1/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void anonymousApiCallDoesNotCreateASession() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/probe")).andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getCookie("JSESSIONID")).isNull();
    }

    @Test
    @WithMockUser
    void authenticatedApiCallIsServed() throws Exception {
        mockMvc.perform(get("/api/v1/probe")).andExpect(status().isOk());
    }

    @Test
    void mutatingCallWithoutCsrfTokenGetsAProblemJson403() throws Exception {
        mockMvc.perform(post("/api/v1/probe"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser
    void mutatingCallEchoingTheXsrfCookieInTheHeaderIsServed() throws Exception {
        // This is what the SPA does: it reads the XSRF-TOKEN cookie and sends the same value as X-XSRF-TOKEN.
        mockMvc.perform(post("/api/v1/probe")
                        .cookie(new Cookie("XSRF-TOKEN", "token-from-cookie"))
                        .header("X-XSRF-TOKEN", "token-from-cookie"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void mutatingCallWithAHeaderThatDiffersFromTheCookieIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/probe")
                        .cookie(new Cookie("XSRF-TOKEN", "token-from-cookie"))
                        .header("X-XSRF-TOKEN", "some-other-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser
    void theFrameworksOwnLogoutPathIsNotAvailableAsASecondWayToSignOut() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/logout")
                        .session(session)
                        .cookie(new Cookie("XSRF-TOKEN", "token-from-cookie"))
                        .header("X-XSRF-TOKEN", "token-from-cookie"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(session.isInvalid()).as("the framework logout must not run").isFalse();
    }

    @Test
    void healthAndDocumentationAreReachableAnonymously() throws Exception {
        // No handler exists in this slice, so a 404 (not a 401) proves security let the request through.
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isNotFound());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }

    @Test
    void otherActuatorEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void aCustomerPassesACustomerOnlyRule() throws Exception {
        mockMvc.perform(get("/api/v1/probe/customers-only")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void anAgentFailsACustomerOnlyRuleWithAProblemJson403() throws Exception {
        mockMvc.perform(get("/api/v1/probe/customers-only"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.detail").value("You are not allowed to perform this action."));
    }

    @Test
    void anAnonymousCallerFailsACustomerOnlyRuleWithA401() throws Exception {
        mockMvc.perform(get("/api/v1/probe/customers-only"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void responsesCarryTheSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/probe"))
                .andExpect(header().string(
                                "Content-Security-Policy",
                                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' data:; font-src 'self' data:; frame-ancestors 'none'"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @RestController
    static class ProbeController {

        @GetMapping("/api/v1/probe")
        String read() {
            return "ok";
        }

        @GetMapping("/api/v1/probe/customers-only")
        @PreAuthorize("hasRole('CUSTOMER')")
        String customersOnly() {
            return "ok";
        }

        @PostMapping("/api/v1/probe")
        String write() {
            return "ok";
        }
    }
}
