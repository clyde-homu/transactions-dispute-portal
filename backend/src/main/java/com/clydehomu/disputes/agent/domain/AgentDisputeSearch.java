package com.clydehomu.disputes.agent.domain;

import com.clydehomu.disputes.dispute.domain.DisputeStatus;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What an agent asked of the work queue, with the default applied: without a status filter, the unassigned and "mine"
 * queues show only open disputes, because a closed dispute is not work. "All" shows every status.
 *
 * @param statuses never null; empty means no status filter
 * @param text a null value means no text filter
 */
public record AgentDisputeSearch(AgentQueue queue, UUID agentId, Set<DisputeStatus> statuses, String text) {

    private static final Set<DisputeStatus> OPEN = Arrays.stream(DisputeStatus.values())
            .filter(DisputeStatus::isOpen)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(DisputeStatus.class)));

    public static AgentDisputeSearch of(AgentQueue queue, UUID agentId, Set<DisputeStatus> requested, String text) {
        boolean defaultToOpen = requested.isEmpty() && queue != AgentQueue.ALL;
        return new AgentDisputeSearch(queue, agentId, defaultToOpen ? Set.copyOf(OPEN) : Set.copyOf(requested), text);
    }
}
