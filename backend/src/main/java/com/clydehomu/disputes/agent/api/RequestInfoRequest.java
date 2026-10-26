package com.clydehomu.disputes.agent.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /agent/disputes/{id}/request-info}: the question the customer will see. */
public record RequestInfoRequest(
        @NotBlank(message = "must not be blank") @Size(max = 1000, message = "must be between 1 and 1000 characters")
        String message) {

    public RequestInfoRequest {
        // Spaces around the question do not count towards the length or get stored.
        message = message == null ? null : message.strip();
    }
}
