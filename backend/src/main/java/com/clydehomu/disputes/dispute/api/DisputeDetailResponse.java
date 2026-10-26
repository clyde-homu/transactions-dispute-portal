package com.clydehomu.disputes.dispute.api;

import com.clydehomu.disputes.dispute.application.DisputeView;
import com.clydehomu.disputes.dispute.domain.Dispute;
import com.clydehomu.disputes.dispute.domain.DisputeReason;
import com.clydehomu.disputes.dispute.domain.DisputeStatus;
import com.clydehomu.disputes.shared.web.ApiFormats;
import com.clydehomu.disputes.transaction.api.TransactionSummaryResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A dispute with the transaction it is about and its timeline, oldest event first. */
public record DisputeDetailResponse(
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
        String description,
        TransactionSummaryResponse transaction,
        List<TimelineEventResponse> timeline,
        boolean canWithdraw) {

    static DisputeDetailResponse from(DisputeView view) {
        Dispute dispute = view.dispute();
        DisputeStatus transactionStatus = view.transactionDisputeStatus();
        return new DisputeDetailResponse(
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
                dispute.getDescription(),
                TransactionSummaryResponse.from(
                        dispute.getTransaction(), transactionStatus == null ? null : transactionStatus.name()),
                dispute.getEvents().stream().map(TimelineEventResponse::from).toList(),
                dispute.getStatus().isOpen());
    }
}
