package com.clydehomu.disputes.agent.application;

import com.clydehomu.disputes.agent.domain.AgentQueue;
import com.clydehomu.disputes.dispute.domain.DisputeStatus;
import java.util.Set;

/** What an agent asked for when listing the work queue; the paging values are range-checked at the API edge. */
public record AgentDisputeQuery(AgentQueue queue, Set<DisputeStatus> statuses, String text, int page, int size) {}
