package com.clydehomu.disputes.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginAttemptTrackerTest {

    private MutableClock clock;
    private LoginAttemptTracker tracker;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-26T08:00:00Z"));
        tracker = new LoginAttemptTracker(clock);
    }

    @Test
    void theFirstFiveAttemptsAreAllowedAndTheSixthIsRefused() {
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(tracker.tryBegin("thandi")).as("attempt %d", attempt).isTrue();
        }

        assertThat(tracker.tryBegin("thandi")).isFalse();
    }

    @Test
    void refusalIsPerUsername() {
        beginTimes("thandi", 5);

        assertThat(tracker.tryBegin("thandi")).isFalse();
        assertThat(tracker.tryBegin("pieter")).isTrue();
    }

    @Test
    void refusalLastsUntilTheWindowEndsAndThenLifts() {
        beginTimes("thandi", 5);

        clock.advance(Duration.ofMinutes(14).plusSeconds(59));
        assertThat(tracker.tryBegin("thandi")).isFalse();

        clock.advance(Duration.ofSeconds(1));
        assertThat(tracker.tryBegin("thandi")).isTrue();
    }

    @Test
    void theWindowStartsAtTheFirstAttemptNotTheLast() {
        tracker.tryBegin("thandi");
        clock.advance(Duration.ofMinutes(10));
        beginTimes("thandi", 4);
        assertThat(tracker.tryBegin("thandi")).isFalse();

        clock.advance(Duration.ofMinutes(5));

        assertThat(tracker.tryBegin("thandi")).isTrue();
    }

    @Test
    void attemptsSpreadOverMoreThanOneWindowDoNotAccumulate() {
        beginTimes("thandi", 4);
        clock.advance(Duration.ofMinutes(15));

        beginTimes("thandi", 4);

        assertThat(tracker.tryBegin("thandi")).isTrue();
    }

    @Test
    void aSuccessfulSignInClearsTheCount() {
        beginTimes("thandi", 4);
        tracker.reset("thandi");

        beginTimes("thandi", 4);

        assertThat(tracker.tryBegin("thandi")).isTrue();
    }

    @Test
    void refusedAttemptsDoNotExtendTheWindow() {
        beginTimes("thandi", 5);
        clock.advance(Duration.ofMinutes(10));
        for (int i = 0; i < 3; i++) {
            assertThat(tracker.tryBegin("thandi")).isFalse();
        }

        clock.advance(Duration.ofMinutes(5));

        assertThat(tracker.tryBegin("thandi")).isTrue();
    }

    @Test
    void retryAfterIsTheTimeLeftInTheWindow() {
        beginTimes("thandi", 5);
        clock.advance(Duration.ofMinutes(4));

        assertThat(tracker.retryAfter("thandi")).isEqualTo(Duration.ofMinutes(11));

        clock.advance(Duration.ofMinutes(11));
        assertThat(tracker.retryAfter("thandi")).isEqualTo(Duration.ZERO);
    }

    @Test
    void retryAfterIsZeroForAnUnknownUsername() {
        assertThat(tracker.retryAfter("nobody")).isEqualTo(Duration.ZERO);
    }

    @Test
    void parallelAttemptsForOneUsernameNeverExceedTheLimit() throws Exception {
        int parallel = 20;
        ExecutorService pool = Executors.newFixedThreadPool(parallel);
        CountDownLatch ready = new CountDownLatch(parallel);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < parallel; i++) {
                Callable<Boolean> attempt = () -> {
                    ready.countDown();
                    go.await();
                    return tracker.tryBegin("thandi");
                };
                results.add(pool.submit(attempt));
            }
            ready.await();
            go.countDown();

            long allowed = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    allowed++;
                }
            }

            assertThat(allowed).isEqualTo(5);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void theMapNeverGrowsPastItsLimitAndDropsTheOldestFirst() {
        LoginAttemptTracker small = new LoginAttemptTracker(clock, 5, Duration.ofMinutes(15), 3);
        for (int i = 0; i < 5; i++) {
            small.tryBegin("first");
        }
        small.tryBegin("second");
        small.tryBegin("third");

        small.tryBegin("fourth");

        assertThat(small.trackedUsernames()).isEqualTo(3);
        assertThat(small.tryBegin("first")).as("evicted, so it starts over").isTrue();
    }

    @Test
    void expiredEntriesAreDroppedBeforeLiveOnesWhenTheMapIsFull() {
        LoginAttemptTracker small = new LoginAttemptTracker(clock, 5, Duration.ofMinutes(15), 3);
        small.tryBegin("stale");
        clock.advance(Duration.ofMinutes(16));
        for (int i = 0; i < 5; i++) {
            small.tryBegin("live-one");
        }
        small.tryBegin("live-two");

        small.tryBegin("live-three");

        assertThat(small.trackedUsernames()).isEqualTo(3);
        assertThat(small.tryBegin("live-one")).as("still limited").isFalse();
    }

    private void beginTimes(String username, int times) {
        for (int i = 0; i < times; i++) {
            tracker.tryBegin(username);
        }
    }
}
