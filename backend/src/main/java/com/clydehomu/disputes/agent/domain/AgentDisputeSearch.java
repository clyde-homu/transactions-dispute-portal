package com.clydehomu.disputes.agent.domain;

import com.clydehomu.disputes.dispute.domain.DisputeStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * What an agent asked of the work queue, with the default applied: without a status filter, the unassigned and "mine"
 * queues show only open disputes, because a closed dispute is not work. "All" shows every status.
 *
 * @param statuses never null; empty means no status filter
 * @param text a null value means no text filter
 * @param overdueAsOf when set, only open disputes due before this instant (the same rule as
 *     {@code Dispute.isOverdue}); null means no overdue filter
 */
public record AgentDisputeSearch(
        AgentQueue queue, UUID agentId, Set<DisputeStatus> statuses, String text, Instant overdueAsOf) {

    public static AgentDisputeSearch of(
            AgentQueue queue, UUID agentId, Set<DisputeStatus> requested, String text, Instant overdueAsOf) {
        boolean defaultToOpen = requested.isEmpty() && queue != AgentQueue.ALL;
        return new AgentDisputeSearch(
                queue,
                agentId,
                defaultToOpen ? Set.copyOf(DisputeStatus.openStatuses()) : Set.copyOf(requested),
                text,
                overdueAsOf);
    }
}
