package com.clydehomu.disputes.agent.api;

import com.clydehomu.disputes.agent.domain.ResolutionOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /agent/disputes/{id}/resolve}. The customer message goes on the customer's timeline; the internal
 * note stays with the back office.
 */
public record ResolveDisputeRequest(
        @NotNull(message = "is required") ResolutionOutcome outcome,

        @NotBlank(message = "must not be blank") @Size(max = 1000, message = "must be between 1 and 1000 characters")
        String customerMessage,

        @NotBlank(message = "must not be blank") @Size(max = 1000, message = "must be between 1 and 1000 characters")
        String internalNote) {

    public ResolveDisputeRequest {
        customerMessage = customerMessage == null ? null : customerMessage.strip();
        internalNote = internalNote == null ? null : internalNote.strip();
    }
}
