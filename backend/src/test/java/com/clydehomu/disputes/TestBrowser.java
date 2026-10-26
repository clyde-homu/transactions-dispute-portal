package com.clydehomu.disputes;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

/**
 * A minimal browser for integration tests: a real HTTP client with a cookie jar, so session cookies, CSRF cookies and
 * their attributes behave exactly as they do for the SPA. Each instance is one browser profile.
 */
public final class TestBrowser {

    private final String baseUrl;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .cookieHandler(cookies)
            .build();

    public TestBrowser(int port) {
        // An IP literal avoids the cookie jar's special handling of dotless host names.
        this.baseUrl = "http://127.0.0.1:" + port;
    }

    public HttpResponse<String> get(String path) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build());
    }

    /** POST with the CSRF header the SPA would send: the current value of the XSRF-TOKEN cookie. */
    public HttpResponse<String> postJson(String path, String json) {
        return postJson(path, json, cookie("XSRF-TOKEN"));
    }

    /** POST with an explicit CSRF header value; null sends none. */
    public HttpResponse<String> postJson(String path, String json, String csrfHeader) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (csrfHeader != null) {
            request.header("X-XSRF-TOKEN", csrfHeader);
        }
        return send(request.build());
    }

    /** Fetches the CSRF cookie, signs in, and fails the test immediately if the credentials are not accepted. */
    public TestBrowser signIn(String username, String password) {
        get("/api/v1/auth/csrf");
        HttpResponse<String> response = postJson(
                "/api/v1/auth/login", "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Sign-in as " + username + " failed with " + response.statusCode());
        }
        return this;
    }

    public HttpResponse<String> getWithRawCookie(String path, String cookieHeader) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Cookie", cookieHeader)
                .GET()
                .build());
    }

    /** Value of a cookie currently in the jar, or null. */
    public String cookie(String name) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals(name))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElse(null);
    }

    public static List<String> setCookies(HttpResponse<String> response) {
        return response.headers().allValues("Set-Cookie");
    }

    public static String setCookie(HttpResponse<String> response, String name) {
        return setCookies(response).stream()
                .filter(c -> c.startsWith(name + "="))
                .findFirst()
                .orElse(null);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("Request failed: " + request.uri(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted: " + request.uri(), e);
        }
    }
}
