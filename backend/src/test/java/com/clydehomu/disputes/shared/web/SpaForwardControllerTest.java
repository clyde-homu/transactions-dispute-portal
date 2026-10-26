package com.clydehomu.disputes.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clydehomu.disputes.shared.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SpaForwardController.class)
@Import(SecurityConfig.class)
class SpaForwardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/",
                "/login",
                "/transactions",
                "/transactions/6f1c0d0e-3b52-4d3e-9f0a-7a1c2b3d4e5f",
                "/transactions/6f1c0d0e-3b52-4d3e-9f0a-7a1c2b3d4e5f/dispute",
                "/disputes/6f1c0d0e-3b52-4d3e-9f0a-7a1c2b3d4e5f"
            })
    void clientSideRoutesForwardToTheSpaShell(String route) throws Exception {
        mockMvc.perform(get(route)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/assets/app.4f3a.js", "/favicon.ico", "/robots.txt", "/a/b/c/d/e"})
    void filesAndNonRoutesAreNotForwarded(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isNotFound()).andExpect(forwardedUrl(null));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"/v3/api-docs", "/v3/api-docs/swagger-config", "/swagger-ui/index.html", "/swagger-ui.html"})
    void documentationPathsAreNotForwarded(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isNotFound()).andExpect(forwardedUrl(null));
    }

    @Test
    void apiPathsAreNeverForwardedAndStillRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/unknown"))
                .andExpect(status().isUnauthorized())
                .andExpect(forwardedUrl(null));
    }

    @Test
    void actuatorPathsAreNeverForwarded() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isNotFound())
                .andExpect(forwardedUrl(null));
    }
}
