package com.clydehomu.disputes.dispute.api;

import com.clydehomu.disputes.dispute.domain.Dispute;
import com.clydehomu.disputes.dispute.domain.DisputeReason;
import com.clydehomu.disputes.dispute.domain.DisputeStatus;
import com.clydehomu.disputes.shared.web.ApiFormats;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of the dispute history. The merchant comes from the transaction, which the list query fetches. {@code dueAt}
 * is the date the bank aims to answer by; customers get no overdue flag (ADR 0007).
 */
public record DisputeSummaryResponse(
        UUID id,
        String reference,
        DisputeStatus status,
        DisputeReason reasonCode,
        String reasonLabel,
        String disputedAmount,
        String currency,
        String merchantName,
        Instant createdAt,
        Instant updatedAt,
        Instant dueAt) {

    static DisputeSummaryResponse from(Dispute dispute) {
        return new DisputeSummaryResponse(
                dispute.getId(),
                dispute.getReference(),
                dispute.getStatus(),
                dispute.getReason(),
                dispute.getReason().label(),
                ApiFormats.money(dispute.getDisputedAmount()),
                dispute.getCurrency(),
                dispute.getTransaction().getMerchantName(),
                ApiFormats.timestamp(dispute.getCreatedAt()),
                ApiFormats.timestamp(dispute.getUpdatedAt()),
                ApiFormats.timestamp(dispute.getDueAt()));
    }
}
