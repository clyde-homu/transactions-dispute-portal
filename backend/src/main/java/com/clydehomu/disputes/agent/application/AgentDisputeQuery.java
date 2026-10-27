package com.clydehomu.disputes.agent.application;

import com.clydehomu.disputes.agent.domain.AgentQueue;
import com.clydehomu.disputes.agent.domain.AgentQueueSort;
import com.clydehomu.disputes.dispute.domain.DisputeStatus;
import java.util.Set;

/**
 * What an agent asked for when listing the work queue; the paging values are range-checked at the API edge.
 *
 * @param overdueOnly keep only open disputes that are past their due date
 */
public record AgentDisputeQuery(
        AgentQueue queue,
        Set<DisputeStatus> statuses,
        String text,
        boolean overdueOnly,
        AgentQueueSort sort,
        int page,
        int size) {}
