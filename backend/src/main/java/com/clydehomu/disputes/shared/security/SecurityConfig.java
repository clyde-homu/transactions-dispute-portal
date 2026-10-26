package com.clydehomu.disputes.shared.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Baseline web security: the API requires a session, everything the SPA and operations need is public, headers are
 * locked down, and 401/403 flow through the same problem-detail handler as every other error. Login, roles and
 * method security are added by the auth feature.
 */
@Configuration
public class SecurityConfig {

    static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                    + "img-src 'self' data:; font-src 'self' data:; frame-ancestors 'none'";

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver)
            throws Exception {
        http.csrf(csrf -> csrf.spa())
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)))
                // The SPA has no "return to the page you wanted" flow. With the default cache, every anonymous 401
                // would create an HTTP session just to remember the request.
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .authorizeHttpRequests(requests -> requests.requestMatchers("/api/**")
                        .authenticated()
                        .requestMatchers(
                                "/actuator/health/**",
                                "/actuator/info",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers("/actuator/**")
                        .authenticated()
                        // SPA shell, hashed assets and client-side routes. Nothing else is readable anonymously.
                        .requestMatchers(HttpMethod.GET, "/**")
                        .permitAll()
                        .anyRequest()
                        .denyAll())
                // Delegating to the MVC resolver sends security failures through GlobalExceptionHandler, so the
                // body is the same problem document as every other error.
                .exceptionHandling(handling -> handling.authenticationEntryPoint((request, response, exception) ->
                                exceptionResolver.resolveException(request, response, null, exception))
                        .accessDeniedHandler((request, response, exception) ->
                                exceptionResolver.resolveException(request, response, null, exception)));
        return http.build();
    }

    /**
     * Declared only so Spring Boot does not create a default user with a generated password. The auth feature replaces
     * it with the database-backed service.
     */
    @Bean
    UserDetailsService noUsers() {
        return username -> {
            throw new UsernameNotFoundException("No user store is configured yet");
        };
    }
}
