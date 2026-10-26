package com.clydehomu.disputes.shared.error;

import java.time.Duration;

/** A refusal that tells the client when to come back; it becomes a 429-style problem with a {@code Retry-After} header. */
public class RateLimitedException extends ApiException {

    private final Duration retryAfter;

    public RateLimitedException(ErrorCode code, String message, Duration retryAfter) {
        super(code, message);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
