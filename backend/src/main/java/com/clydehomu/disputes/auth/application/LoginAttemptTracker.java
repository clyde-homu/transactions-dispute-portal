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
 * Limits sign-in attempts per username: at most {@code maxAttempts} inside one window, and then every further attempt
 * is refused until that window ends, even with the right password.
 *
 * <p>An attempt is counted <em>before</em> the password is checked, in the same synchronized step as the limit check.
 * Password hashing takes about 100 ms, so counting afterwards would let every request that arrives in that time pass
 * the check and multiply the number of guesses. A successful sign-in resets the count.
 *
 * <p>State is in memory, so it resets on restart and is not shared between instances. The map is bounded: when it is
 * full, expired entries go first, then the oldest. Refused attempts do not extend the window, so an attacker cannot
 * keep a customer locked out by hammering the endpoint.
 */
@Component
public class LoginAttemptTracker {

    static final int DEFAULT_MAX_ATTEMPTS = 5;
    static final Duration DEFAULT_WINDOW = Duration.ofMinutes(15);
    static final int DEFAULT_MAX_ENTRIES = 10_000;

    private record Attempts(Instant windowStart, int count) {}

    private final Clock clock;
    private final int maxAttempts;
    private final Duration window;
    private final int maxEntries;
    // Insertion order doubles as age order, which is what eviction needs.
    private final Map<String, Attempts> byUsername = new LinkedHashMap<>();

    @Autowired
    public LoginAttemptTracker(Clock clock) {
        this(clock, DEFAULT_MAX_ATTEMPTS, DEFAULT_WINDOW, DEFAULT_MAX_ENTRIES);
    }

    LoginAttemptTracker(Clock clock, int maxAttempts, Duration window, int maxEntries) {
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.maxEntries = maxEntries;
    }

    /** Counts this attempt and returns true, or returns false (counting nothing) when the limit is already reached. */
    public synchronized boolean tryBegin(String username) {
        Instant now = clock.instant();
        Attempts current = byUsername.get(username);
        if (current == null || isExpired(current, now)) {
            byUsername.remove(username);
            makeRoom(now);
            byUsername.put(username, new Attempts(now, 1));
            return true;
        }
        if (current.count() >= maxAttempts) {
            return false;
        }
        // Re-putting keeps the original window start; the entry stays where it is in the age order.
        byUsername.put(username, new Attempts(current.windowStart(), current.count() + 1));
        return true;
    }

    /** How long until attempts are accepted again; zero when the username is not currently limited. */
    public synchronized Duration retryAfter(String username) {
        Attempts current = byUsername.get(username);
        if (current == null) {
            return Duration.ZERO;
        }
        Duration remaining =
                Duration.between(clock.instant(), current.windowStart().plus(window));
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /** Called after a successful sign-in: the customer proved who they are, so earlier failures no longer count. */
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
