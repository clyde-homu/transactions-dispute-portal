package com.clydehomu.disputes.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clydehomu.disputes.auth.application.AuthService;
import com.clydehomu.disputes.auth.application.AuthenticatedUser;
import com.clydehomu.disputes.auth.domain.AppUser;
import com.clydehomu.disputes.auth.domain.Role;
import com.clydehomu.disputes.auth.infrastructure.SessionAuthenticator;
import com.clydehomu.disputes.shared.error.ApiException;
import com.clydehomu.disputes.shared.error.ErrorCode;
import com.clydehomu.disputes.shared.security.SecurityConfig;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, SessionAuthenticator.class})
class AuthControllerTest {

    private static final UUID THANDI_ID = UUID.fromString("6d0b8a51-3f2c-4e7a-9b14-0c5e2f7a9d31");
    private static final String TOKEN = "token-from-cookie";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    private AuthenticatedUser thandi;

    @BeforeEach
    void setUp() {
        AppUser stored = new AppUser("thandi", "hash", "Thandi Mokoena", Role.CUSTOMER, Instant.now());
        ReflectionTestUtils.setField(stored, "id", THANDI_ID);
        thandi = new AuthenticatedUser(stored);
    }

    // ---- GET /auth/csrf ----

    @Test
    void csrfIssuesAScriptReadableLaxCookieAndNoBody() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie cookie = xsrfCookie(result);
        assertThat(cookie.getValue()).isNotBlank();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(cookie.isHttpOnly()).isFalse();
        assertThat(result.getResponse().getContentAsString()).isEmpty();
    }

    // ---- POST /auth/login ----

    @Test
    void loginReturnsTheCurrentUserAndStartsASession() throws Exception {
        when(authService.login("thandi", "demo-password")).thenReturn(authenticated());

        MvcResult result = mockMvc.perform(withCsrf(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"thandi\",\"password\":\"demo-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(THANDI_ID.toString()))
                .andExpect(jsonPath("$.username").value("thandi"))
                .andExpect(jsonPath("$.displayName").value("Thandi Mokoena"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                .isNotNull();
    }

    @Test
    void loginRotatesTheCsrfToken() throws Exception {
        when(authService.login("thandi", "demo-password")).thenReturn(authenticated());

        MvcResult result = mockMvc.perform(withCsrf(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"thandi\",\"password\":\"demo-password\"}"))
                .andReturn();

        Cookie rotated = xsrfCookie(result);
        assertThat(rotated.getValue()).isNotBlank().isNotEqualTo(TOKEN);
        assertThat(rotated.getAttribute("SameSite")).isEqualTo("Lax");
    }

    @Test
    void loginWithBadCredentialsIsAProblem401() throws Exception {
        when(authService.login(any(), any()))
                .thenThrow(new ApiException(ErrorCode.BAD_CREDENTIALS, "The username or password is incorrect."));

        mockMvc.perform(withCsrf(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"thandi\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("The username or password is incorrect."))
                .andExpect(jsonPath("$.instance").value("/api/v1/auth/login"));
    }

    @Test
    void aFailedLoginDoesNotStartASession() throws Exception {
        when(authService.login(any(), any()))
                .thenThrow(new ApiException(ErrorCode.BAD_CREDENTIALS, "The username or password is incorrect."));

        MvcResult result = mockMvc.perform(withCsrf(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"thandi\",\"password\":\"wrong\"}"))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void loginWhenThrottledIsAProblem429() throws Exception {
        when(authService.login(any(), any()))
                .thenThrow(new ApiException(
                        ErrorCode.TOO_MANY_LOGIN_ATTEMPTS,
                        "Too many failed sign-in attempts. Try again in a few minutes."));

        mockMvc.perform(withCsrf(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"thandi\",\"password\":\"demo-password\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"));
    }

    @Test
    void loginWithBlankFieldsListsEachFailingField() throws Exception {
        mockMvc.perform(withCsrf(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\" \",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[1].field").value("username"));

        verifyNoInteractions(authService);
    }

    @Test
    void loginWithOverlongFieldsIsRejected() throws Exception {
        String body = "{\"username\":\"" + "u".repeat(51) + "\",\"password\":\"" + "p".repeat(73) + "\"}";

        mockMvc.perform(withCsrf(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));

        verifyNoInteractions(authService);
    }

    @Test
    void loginWithoutABodyOrWithMalformedJsonIsAValidationProblem() throws Exception {
        mockMvc.perform(withCsrf(post("/api/v1/auth/login")).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(withCsrf(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void loginWithoutACsrfTokenIsAProblem403AndNeverChecksCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"thandi\",\"password\":\"demo-password\"}"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(authService);
    }

    // ---- POST /auth/logout ----

    @Test
    void logoutEndsTheSessionAndRotatesTheCsrfToken() throws Exception {
        MockHttpSession session = new MockHttpSession();

        MvcResult result = mockMvc.perform(
                        withCsrf(post("/api/v1/auth/logout")).session(session).with(user(thandi)))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(session.isInvalid()).isTrue();
        Cookie rotated = xsrfCookie(result);
        assertThat(rotated.getValue()).isNotBlank().isNotEqualTo(TOKEN);
        assertThat(rotated.getAttribute("SameSite")).isEqualTo("Lax");
    }

    @Test
    void logoutWithoutASessionStillReturns204() throws Exception {
        mockMvc.perform(withCsrf(post("/api/v1/auth/logout"))).andExpect(status().isNoContent());
    }

    @Test
    void logoutWithoutACsrfTokenIsAProblem403() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ---- GET /auth/me ----

    @Test
    void meReturnsTheSignedInUser() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").with(user(thandi)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(THANDI_ID.toString()))
                .andExpect(jsonPath("$.username").value("thandi"))
                .andExpect(jsonPath("$.displayName").value("Thandi Mokoena"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void meWithoutASessionIsAProblem401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.detail").value("Sign in to continue."))
                .andExpect(jsonPath("$.instance").value("/api/v1/auth/me"));
    }

    // ---- helpers ----

    /** What the SPA does: echo the XSRF-TOKEN cookie in the X-XSRF-TOKEN header. */
    private static MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) {
        return request.cookie(new Cookie("XSRF-TOKEN", TOKEN)).header("X-XSRF-TOKEN", TOKEN);
    }

    private Authentication authenticated() {
        return UsernamePasswordAuthenticationToken.authenticated(thandi, null, thandi.getAuthorities());
    }

    /** The single XSRF-TOKEN cookie the response set; fails if there is none or more than one. */
    private static Cookie xsrfCookie(MvcResult result) {
        List<Cookie> cookies = Arrays.stream(result.getResponse().getCookies())
                .filter(c -> c.getName().equals("XSRF-TOKEN"))
                .toList();
        assertThat(cookies).hasSize(1);
        return cookies.getFirst();
    }
}
