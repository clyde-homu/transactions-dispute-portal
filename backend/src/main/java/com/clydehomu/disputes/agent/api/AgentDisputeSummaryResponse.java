package com.clydehomu.disputes.agent.api;

import com.clydehomu.disputes.agent.application.AgentDisputeSummaryView;
import com.clydehomu.disputes.dispute.domain.Dispute;
import com.clydehomu.disputes.dispute.domain.DisputeReason;
import com.clydehomu.disputes.dispute.domain.DisputeStatus;
import com.clydehomu.disputes.shared.web.ApiFormats;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of the work queue. {@code assignedAgentName} is null until an agent claims the dispute; {@code overdue} is true
 * for an open dispute past {@code dueAt}.
 */
public record AgentDisputeSummaryResponse(
        UUID id,
        String reference,
        DisputeStatus status,
        DisputeReason reasonCode,
        String reasonLabel,
        String disputedAmount,
        String currency,
        String merchantName,
        String customerName,
        String assignedAgentName,
        Instant createdAt,
        Instant updatedAt,
        Instant dueAt,
        boolean overdue) {

    static AgentDisputeSummaryResponse from(AgentDisputeSummaryView view) {
        Dispute dispute = view.dispute();
        return new AgentDisputeSummaryResponse(
                dispute.getId(),
                dispute.getReference(),
                dispute.getStatus(),
                dispute.getReason(),
                dispute.getReason().label(),
                ApiFormats.money(dispute.getDisputedAmount()),
                dispute.getCurrency(),
                dispute.getTransaction().getMerchantName(),
                view.customerName(),
                view.assignedAgentName(),
                ApiFormats.timestamp(dispute.getCreatedAt()),
                ApiFormats.timestamp(dispute.getUpdatedAt()),
                ApiFormats.timestamp(dispute.getDueAt()),
                view.overdue());
    }
}
