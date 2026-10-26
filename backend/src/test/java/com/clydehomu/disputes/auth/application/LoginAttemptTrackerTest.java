package com.clydehomu.disputes.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
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
    void anUnknownUsernameIsNotBlocked() {
        assertThat(tracker.isBlocked("thandi")).isFalse();
    }

    @Test
    void fourFailuresDoNotBlock() {
        failTimes("thandi", 4);

        assertThat(tracker.isBlocked("thandi")).isFalse();
    }

    @Test
    void theFifthFailureBlocks() {
        failTimes("thandi", 5);

        assertThat(tracker.isBlocked("thandi")).isTrue();
    }

    @Test
    void blockingIsPerUsername() {
        failTimes("thandi", 5);

        assertThat(tracker.isBlocked("pieter")).isFalse();
    }

    @Test
    void blockLastsUntilTheWindowEndsAndThenLifts() {
        failTimes("thandi", 5);

        clock.advance(Duration.ofMinutes(14).plusSeconds(59));
        assertThat(tracker.isBlocked("thandi")).isTrue();

        clock.advance(Duration.ofSeconds(1));
        assertThat(tracker.isBlocked("thandi")).isFalse();
    }

    @Test
    void theWindowStartsAtTheFirstFailureNotTheLast() {
        tracker.recordFailure("thandi");
        clock.advance(Duration.ofMinutes(10));
        failTimes("thandi", 4);

        assertThat(tracker.isBlocked("thandi")).isTrue();
        clock.advance(Duration.ofMinutes(5));
        assertThat(tracker.isBlocked("thandi")).isFalse();
    }

    @Test
    void failuresSpreadOverMoreThanOneWindowDoNotAccumulate() {
        failTimes("thandi", 4);
        clock.advance(Duration.ofMinutes(15));

        tracker.recordFailure("thandi");

        assertThat(tracker.isBlocked("thandi")).isFalse();
    }

    @Test
    void aSuccessfulLoginClearsTheCount() {
        failTimes("thandi", 4);
        tracker.reset("thandi");
        failTimes("thandi", 4);

        assertThat(tracker.isBlocked("thandi")).isFalse();
    }

    @Test
    void attemptsWhileBlockedDoNotExtendTheBlock() {
        failTimes("thandi", 5);
        clock.advance(Duration.ofMinutes(10));
        failTimes("thandi", 3);

        clock.advance(Duration.ofMinutes(5));

        assertThat(tracker.isBlocked("thandi")).isFalse();
    }

    @Test
    void theMapNeverGrowsPastItsLimitAndDropsTheOldestFirst() {
        LoginAttemptTracker small = new LoginAttemptTracker(clock, 5, Duration.ofMinutes(15), 3);
        for (int i = 0; i < 5; i++) {
            small.recordFailure("first");
        }
        small.recordFailure("second");
        small.recordFailure("third");

        small.recordFailure("fourth");

        assertThat(small.trackedUsernames()).isEqualTo(3);
        assertThat(small.isBlocked("first")).isFalse();
    }

    @Test
    void expiredEntriesAreDroppedBeforeLiveOnesWhenTheMapIsFull() {
        LoginAttemptTracker small = new LoginAttemptTracker(clock, 5, Duration.ofMinutes(15), 3);
        small.recordFailure("stale");
        clock.advance(Duration.ofMinutes(16));
        for (int i = 0; i < 5; i++) {
            small.recordFailure("live-one");
        }
        small.recordFailure("live-two");

        small.recordFailure("live-three");

        assertThat(small.trackedUsernames()).isEqualTo(3);
        assertThat(small.isBlocked("live-one")).isTrue();
    }

    private void failTimes(String username, int times) {
        for (int i = 0; i < times; i++) {
            tracker.recordFailure(username);
        }
    }
}
