package com.clydehomu.disputes.dispute.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /disputes/{id}/responses}: the customer's reply to an information request. */
public record CustomerResponseRequest(
        @NotBlank(message = "must not be blank") @Size(max = 1000, message = "must be between 1 and 1000 characters")
        String message) {

    public CustomerResponseRequest {
        // Spaces around the reply do not count towards the length or get stored.
        message = message == null ? null : message.strip();
    }
}
