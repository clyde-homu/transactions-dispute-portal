package com.clydehomu.disputes.dispute.application;

import com.clydehomu.disputes.dispute.domain.DisputeReason;
import java.math.BigDecimal;
import java.util.UUID;

/** What the customer asked for when raising a dispute; the shape has already been validated at the API edge. */
public record CreateDisputeCommand(UUID transactionId, DisputeReason reason, BigDecimal amount, String description) {}
