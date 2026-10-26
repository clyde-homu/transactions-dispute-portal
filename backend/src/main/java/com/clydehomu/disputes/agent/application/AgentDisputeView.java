package com.clydehomu.disputes.agent.application;

import com.clydehomu.disputes.agent.domain.AgentAction;
import com.clydehomu.disputes.dispute.domain.Dispute;
import com.clydehomu.disputes.dispute.domain.DisputeEvent;
import com.clydehomu.disputes.dispute.domain.DisputeStatus;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A dispute as the back office sees it: the entity with its transaction and timeline loaded, the display names behind
 * every user id on it, and what the calling agent may do next.
 *
 * @param transactionDisputeStatus the status of the transaction's current (non-withdrawn) dispute
 * @param userNames display names by user id, for the customer, the assignee and every actor on the timeline
 */
public record AgentDisputeView(
        Dispute dispute,
        DisputeStatus transactionDisputeStatus,
        Map<UUID, String> userNames,
        Set<AgentAction> availableActions) {

    public String customerName() {
        return nameOf(dispute.getCustomerId());
    }

    /** Null while the dispute is unassigned. */
    public String assignedAgentName() {
        return nameOf(dispute.getAssignedAgentId());
    }

    /** Null for events without a recorded actor. */
    public String actorName(DisputeEvent event) {
        return nameOf(event.getActorId());
    }

    private String nameOf(UUID userId) {
        return userId == null ? null : userNames.get(userId);
    }
}
