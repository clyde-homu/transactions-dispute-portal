package com.clydehomu.disputes.dispute.api;

import com.clydehomu.disputes.dispute.application.CreateDisputeCommand;
import com.clydehomu.disputes.dispute.domain.DisputeReason;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Body of {@code POST /disputes}. The amount arrives as a JSON string ("250.00") and is bound to a decimal, never a
 * floating-point number. Whether the amount exceeds the transaction's is checked later, once the transaction is loaded.
 */
public record CreateDisputeRequest(
        @NotNull(message = "is required") UUID transactionId,
        @NotNull(message = "is required") DisputeReason reasonCode,

        @NotNull(message = "is required")
        @DecimalMin(value = "0.01", message = "must be greater than 0")
        @Digits(integer = 15, fraction = 2, message = "must have at most 2 decimal places")
        BigDecimal amount,

        @NotBlank(message = "must not be blank")
        @Size(min = 20, max = 1000, message = "must be between 20 and 1000 characters")
        String description) {

    public CreateDisputeRequest {
        // Leading and trailing spaces do not count towards the 20-character minimum or get stored.
        description = description == null ? null : description.strip();
    }

    CreateDisputeCommand toCommand() {
        return new CreateDisputeCommand(transactionId, reasonCode, amount, description);
    }
}
