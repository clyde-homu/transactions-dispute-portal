package com.clydehomu.disputes.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clydehomu.disputes.shared.error.ApiException;
import com.clydehomu.disputes.shared.error.ErrorCode;
import com.clydehomu.disputes.shared.error.RateLimitedException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class AuthServiceTest {

    private AuthenticationManager authenticationManager;
    private LoginAttemptTracker attempts;
    private AuthService service;

    @BeforeEach
    void setUp() {
        authenticationManager = mock(AuthenticationManager.class);
        attempts = new LoginAttemptTracker(new MutableClock(Instant.parse("2026-10-26T08:00:00Z")));
        service = new AuthService(authenticationManager, attempts);
    }

    @Test
    void returnsTheAuthenticationOnSuccess() {
        Authentication ok = UsernamePasswordAuthenticationToken.authenticated("thandi", null, List.of());
        doReturn(ok).when(authenticationManager).authenticate(any());

        assertThat(service.login("thandi", "demo-password")).isSameAs(ok);
    }

    @Test
    void aWrongPasswordIsBadCredentials() {
        doThrow(new BadCredentialsException("nope")).when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> service.login("thandi", "wrong")).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getCode()).isEqualTo(ErrorCode.BAD_CREDENTIALS);
            assertThat(e.getMessage()).isEqualTo("The username or password is incorrect.");
        });
    }

    @Test
    void anUnknownUserGetsExactlyTheSameErrorAsAWrongPassword() {
        when(authenticationManager.authenticate(any())).thenThrow(new UsernameNotFoundException("no such user"));

        assertThatThrownBy(() -> service.login("nobody", "whatever")).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getCode()).isEqualTo(ErrorCode.BAD_CREDENTIALS);
            assertThat(e.getMessage()).isEqualTo("The username or password is incorrect.");
        });
    }

    @Test
    void aPasswordLongerThanBcryptCanHoldIsRejectedWithoutAskingTheManager() {
        // 40 characters, 80 bytes.
        String tooLong = "é".repeat(40);

        assertThatThrownBy(() -> service.login("thandi", tooLong))
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.BAD_CREDENTIALS));
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void afterFiveFailuresEvenTheRightPasswordGets429() {
        doThrow(new BadCredentialsException("nope")).when(authenticationManager).authenticate(any());
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.login("thandi", "wrong")).isInstanceOf(ApiException.class);
        }
        Authentication ok = UsernamePasswordAuthenticationToken.authenticated("thandi", null, List.of());
        doReturn(ok).when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> service.login("thandi", "demo-password"))
                .isInstanceOfSatisfying(RateLimitedException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
                    assertThat(e.getStatus().value()).isEqualTo(429);
                    assertThat(e.getRetryAfter()).isEqualTo(Duration.ofMinutes(15));
                });
    }

    @Test
    void aBlockedAttemptNeverReachesTheAuthenticationManager() {
        for (int i = 0; i < 5; i++) {
            attempts.tryBegin("thandi");
        }

        assertThatThrownBy(() -> service.login("thandi", "demo-password")).isInstanceOf(ApiException.class);
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void parallelGuessesAreCountedBeforeTheSlowPasswordCheckSoOnlyFiveGetThrough() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger passwordChecks = new AtomicInteger();
        when(authenticationManager.authenticate(any())).thenAnswer(call -> {
            passwordChecks.incrementAndGet();
            release.await(10, TimeUnit.SECONDS);
            throw new BadCredentialsException("nope");
        });
        int callers = 20;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                futures.add(pool.submit(() -> service.login("thandi", "guess")));
            }
            // The five allowed callers are stuck inside the password check; the other fifteen are refused at once.
            awaitUntil(() -> futures.stream().filter(Future::isDone).count() == callers - 5L);
            awaitUntil(() -> passwordChecks.get() == 5);
            release.countDown();

            int refused = 0;
            int wrongPassword = 0;
            for (Future<?> future : futures) {
                try {
                    future.get(10, TimeUnit.SECONDS);
                } catch (java.util.concurrent.ExecutionException failure) {
                    if (failure.getCause() instanceof RateLimitedException) {
                        refused++;
                    } else if (failure.getCause() instanceof ApiException api
                            && api.getCode() == ErrorCode.BAD_CREDENTIALS) {
                        wrongPassword++;
                    }
                }
            }

            assertThat(passwordChecks.get()).isEqualTo(5);
            assertThat(wrongPassword).isEqualTo(5);
            assertThat(refused).isEqualTo(callers - 5);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aSuccessfulLoginResetsTheFailureCount() {
        doThrow(new BadCredentialsException("nope")).when(authenticationManager).authenticate(any());
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> service.login("thandi", "wrong")).isInstanceOf(ApiException.class);
        }
        Authentication ok = UsernamePasswordAuthenticationToken.authenticated("thandi", null, List.of());
        doReturn(ok).when(authenticationManager).authenticate(any());
        service.login("thandi", "demo-password");

        doThrow(new BadCredentialsException("nope")).when(authenticationManager).authenticate(any());
        assertThatThrownBy(() -> service.login("thandi", "wrong"))
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.BAD_CREDENTIALS));
    }

    private static void awaitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Condition not reached in time");
            }
            Thread.sleep(5);
        }
    }
}
