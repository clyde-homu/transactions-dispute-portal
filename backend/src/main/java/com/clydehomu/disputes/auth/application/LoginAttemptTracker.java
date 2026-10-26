package com.clydehomu.disputes.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Counts failed sign-ins per username. After {@code maxFailures} failures inside one window the username is blocked
 * until that window ends, even for the right password.
 *
 * <p>State is in memory, so it resets on restart and is not shared between instances. The map is bounded: when it is
 * full, expired entries go first, then the oldest. Blocked attempts do not extend the window, so an attacker cannot
 * keep a customer locked out by hammering the endpoint.
 */
@Component
public class LoginAttemptTracker {

    static final int DEFAULT_MAX_FAILURES = 5;
    static final Duration DEFAULT_WINDOW = Duration.ofMinutes(15);
    static final int DEFAULT_MAX_ENTRIES = 10_000;

    private record Attempts(Instant windowStart, int failures) {}

    private final Clock clock;
    private final int maxFailures;
    private final Duration window;
    private final int maxEntries;
    // Insertion order doubles as age order, which is what eviction needs.
    private final Map<String, Attempts> byUsername = new LinkedHashMap<>();

    @Autowired
    public LoginAttemptTracker(Clock clock) {
        this(clock, DEFAULT_MAX_FAILURES, DEFAULT_WINDOW, DEFAULT_MAX_ENTRIES);
    }

    LoginAttemptTracker(Clock clock, int maxFailures, Duration window, int maxEntries) {
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.window = window;
        this.maxEntries = maxEntries;
    }

    public synchronized boolean isBlocked(String username) {
        Attempts attempts = byUsername.get(username);
        return attempts != null && attempts.failures() >= maxFailures && !isExpired(attempts, clock.instant());
    }

    public synchronized void recordFailure(String username) {
        Instant now = clock.instant();
        Attempts current = byUsername.get(username);
        if (current == null || isExpired(current, now)) {
            byUsername.remove(username);
            makeRoom(now);
            byUsername.put(username, new Attempts(now, 1));
        } else {
            // Re-putting keeps the original window start; the entry stays where it is in the age order.
            byUsername.put(username, new Attempts(current.windowStart(), current.failures() + 1));
        }
    }

    public synchronized void reset(String username) {
        byUsername.remove(username);
    }

    synchronized int trackedUsernames() {
        return byUsername.size();
    }

    private boolean isExpired(Attempts attempts, Instant now) {
        return !now.isBefore(attempts.windowStart().plus(window));
    }

    private void makeRoom(Instant now) {
        if (byUsername.size() < maxEntries) {
            return;
        }
        byUsername.values().removeIf(attempts -> isExpired(attempts, now));
        Iterator<String> oldestFirst = byUsername.keySet().iterator();
        while (byUsername.size() >= maxEntries && oldestFirst.hasNext()) {
            oldestFirst.next();
            oldestFirst.remove();
        }
    }
}
