package com.clydehomu.disputes.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.clydehomu.disputes.PostgresIntegrationSupport;
import com.clydehomu.disputes.TestBrowser;
import com.clydehomu.disputes.auth.domain.AppUser;
import com.clydehomu.disputes.auth.domain.Role;
import com.clydehomu.disputes.auth.infrastructure.AppUserRepository;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

/** The whole sign-in lifecycle over real HTTP, cookies included. Users are created here, not taken from the seeder. */
class AuthFlowIT extends PostgresIntegrationSupport {

    private static final String PASSWORD = "flow-test-password";
    private static final String CUSTOMER = "flow-customer";
    private static final String LOCKOUT_VICTIM = "flow-lockout";

    @LocalServerPort
    private int port;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void createUsers() {
        ensureUser(CUSTOMER, "Flow Customer");
        ensureUser(LOCKOUT_VICTIM, "Flow Lockout");
    }

    @Test
    void csrfThenLoginThenMeThenLogoutThenMeIsUnauthorised() {
        TestBrowser browser = new TestBrowser(port);

        HttpResponse<String> csrf = browser.get("/api/v1/auth/csrf");
        assertThat(csrf.statusCode()).isEqualTo(204);
        String csrfCookie = TestBrowser.setCookie(csrf, "XSRF-TOKEN");
        assertThat(csrfCookie).contains("Path=/").contains("SameSite=Lax").doesNotContain("HttpOnly");
        String tokenBeforeLogin = browser.cookie("XSRF-TOKEN");
        assertThat(tokenBeforeLogin).isNotBlank();

        HttpResponse<String> anonymousMe = browser.get("/api/v1/auth/me");
        assertThat(anonymousMe.statusCode()).isEqualTo(401);
        assertThat(anonymousMe.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(code(anonymousMe)).isEqualTo("UNAUTHENTICATED");
        assertThat(anonymousMe.headers().firstValue("X-Request-Id")).isPresent();
        assertThat(TestBrowser.setCookie(anonymousMe, "JSESSIONID"))
                .as("no session for anonymous")
                .isNull();

        HttpResponse<String> login = browser.postJson("/api/v1/auth/login", credentials(CUSTOMER, PASSWORD));
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(login.body(), "$.username")).isEqualTo(CUSTOMER);
        assertThat(JsonPath.<String>read(login.body(), "$.displayName")).isEqualTo("Flow Customer");
        assertThat(JsonPath.<String>read(login.body(), "$.role")).isEqualTo("CUSTOMER");
        assertThat(JsonPath.<String>read(login.body(), "$.id")).isNotBlank();
        assertThat(login.body()).doesNotContain("password");
        assertThat(TestBrowser.setCookie(login, "JSESSIONID"))
                .contains("HttpOnly")
                .contains("SameSite=Lax");
        assertThat(TestBrowser.setCookies(login).stream().filter(c -> c.startsWith("XSRF-TOKEN=")))
                .as("exactly one rotated CSRF cookie")
                .hasSize(1);
        String tokenAfterLogin = browser.cookie("XSRF-TOKEN");
        assertThat(tokenAfterLogin).isNotEqualTo(tokenBeforeLogin);
        String sessionAfterLogin = browser.cookie("JSESSIONID");

        HttpResponse<String> me = browser.get("/api/v1/auth/me");
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(me.body(), "$.username")).isEqualTo(CUSTOMER);

        HttpResponse<String> logout = browser.postJson("/api/v1/auth/logout", "");
        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(browser.cookie("XSRF-TOKEN")).as("token rotated on logout").isNotEqualTo(tokenAfterLogin);

        HttpResponse<String> meAfterLogout = browser.get("/api/v1/auth/me");
        assertThat(meAfterLogout.statusCode()).isEqualTo(401);
        assertThat(code(meAfterLogout)).isEqualTo("UNAUTHENTICATED");

        // The old session id must be dead on the server, not just forgotten by this client.
        HttpResponse<String> replay =
                new TestBrowser(port).getWithRawCookie("/api/v1/auth/me", "JSESSIONID=" + sessionAfterLogin);
        assertThat(replay.statusCode()).isEqualTo(401);
    }

    @Test
    void signingInAgainIssuesANewSessionId() {
        TestBrowser browser = new TestBrowser(port);
        browser.get("/api/v1/auth/csrf");
        browser.postJson("/api/v1/auth/login", credentials(CUSTOMER, PASSWORD));
        String first = browser.cookie("JSESSIONID");

        HttpResponse<String> second = browser.postJson("/api/v1/auth/login", credentials(CUSTOMER, PASSWORD));

        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(browser.cookie("JSESSIONID")).isNotBlank().isNotEqualTo(first);
    }

    @Test
    void loginWithoutTheCsrfHeaderIsForbidden() {
        TestBrowser browser = new TestBrowser(port);
        browser.get("/api/v1/auth/csrf");

        HttpResponse<String> response = browser.postJson("/api/v1/auth/login", credentials(CUSTOMER, PASSWORD), null);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(code(response)).isEqualTo("FORBIDDEN");
        assertThat(TestBrowser.setCookie(response, "JSESSIONID")).isNull();
    }

    @Test
    void loginWithAWrongCsrfHeaderIsForbidden() {
        TestBrowser browser = new TestBrowser(port);
        browser.get("/api/v1/auth/csrf");

        HttpResponse<String> response =
                browser.postJson("/api/v1/auth/login", credentials(CUSTOMER, PASSWORD), "not-the-token");

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(code(response)).isEqualTo("FORBIDDEN");
    }

    @Test
    void anUnknownUserAndAWrongPasswordGetTheSameResponse() {
        TestBrowser browser = new TestBrowser(port);
        browser.get("/api/v1/auth/csrf");

        HttpResponse<String> wrongPassword = browser.postJson("/api/v1/auth/login", credentials(CUSTOMER, "nope"));
        HttpResponse<String> unknownUser = browser.postJson("/api/v1/auth/login", credentials("no-such-user", "nope"));

        assertThat(wrongPassword.statusCode()).isEqualTo(401);
        assertThat(unknownUser.statusCode()).isEqualTo(401);
        assertThat(code(wrongPassword)).isEqualTo("BAD_CREDENTIALS");
        assertThat(JsonPath.<Object>read(unknownUser.body(), "$"))
                .isEqualTo(JsonPath.<Object>read(wrongPassword.body(), "$"));
        assertThat(TestBrowser.setCookie(wrongPassword, "JSESSIONID")).isNull();
    }

    @Test
    void fiveFailuresLockTheUsernameEvenForTheRightPasswordButNotOtherUsers() {
        TestBrowser browser = new TestBrowser(port);
        browser.get("/api/v1/auth/csrf");

        for (int attempt = 1; attempt <= 5; attempt++) {
            HttpResponse<String> failed =
                    browser.postJson("/api/v1/auth/login", credentials(LOCKOUT_VICTIM, "wrong-" + attempt));
            assertThat(failed.statusCode()).as("attempt %d", attempt).isEqualTo(401);
        }

        HttpResponse<String> blocked = browser.postJson("/api/v1/auth/login", credentials(LOCKOUT_VICTIM, PASSWORD));
        assertThat(blocked.statusCode()).isEqualTo(429);
        assertThat(blocked.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(code(blocked)).isEqualTo("TOO_MANY_LOGIN_ATTEMPTS");

        HttpResponse<String> other = browser.postJson("/api/v1/auth/login", credentials(CUSTOMER, PASSWORD));
        assertThat(other.statusCode()).isEqualTo(200);
    }

    @Test
    void aMalformedLoginBodyIsAValidationProblem() {
        TestBrowser browser = new TestBrowser(port);
        browser.get("/api/v1/auth/csrf");

        HttpResponse<String> response = browser.postJson("/api/v1/auth/login", "{\"username\":\"\",\"password\":\"\"}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(code(response)).isEqualTo("VALIDATION_FAILED");
        assertThat(JsonPath.<Integer>read(response.body(), "$.errors.length()")).isEqualTo(2);
    }

    private void ensureUser(String username, String displayName) {
        if (!users.existsByUsername(username)) {
            users.save(
                    new AppUser(username, passwordEncoder.encode(PASSWORD), displayName, Role.CUSTOMER, Instant.now()));
        }
    }

    private static String credentials(String username, String password) {
        return "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password);
    }

    private static String code(HttpResponse<String> response) {
        return JsonPath.read(response.body(), "$.code");
    }
}
