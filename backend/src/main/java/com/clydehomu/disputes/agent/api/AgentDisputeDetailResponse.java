package com.clydehomu.disputes.agent.api;

import com.clydehomu.disputes.agent.application.AgentDisputeView;
import com.clydehomu.disputes.agent.domain.AgentAction;
import com.clydehomu.disputes.dispute.domain.Dispute;
import com.clydehomu.disputes.dispute.domain.DisputeReason;
import com.clydehomu.disputes.dispute.domain.DisputeStatus;
import com.clydehomu.disputes.shared.web.ApiFormats;
import com.clydehomu.disputes.transaction.api.TransactionSummaryResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A dispute with its transaction, its full timeline and what the calling agent may do next. */
public record AgentDisputeDetailResponse(
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
        boolean overdue,
        String description,
        TransactionSummaryResponse transaction,
        List<AgentTimelineEventResponse> timeline,
        List<AgentAction> availableActions) {

    static AgentDisputeDetailResponse from(AgentDisputeView view) {
        Dispute dispute = view.dispute();
        DisputeStatus transactionStatus = view.transactionDisputeStatus();
        return new AgentDisputeDetailResponse(
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
                view.overdue(),
                dispute.getDescription(),
                TransactionSummaryResponse.from(
                        dispute.getTransaction(), transactionStatus == null ? null : transactionStatus.name()),
                dispute.getEvents().stream()
                        .map(event -> AgentTimelineEventResponse.from(event, view))
                        .toList(),
                // Enum order (claim, ask, resolve) keeps the response stable whatever set the caller built.
                view.availableActions().stream().sorted().toList());
    }
}
