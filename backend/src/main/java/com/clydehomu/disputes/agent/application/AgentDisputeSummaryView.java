package com.clydehomu.disputes.agent.application;

import com.clydehomu.disputes.dispute.domain.Dispute;

/**
 * One row of the work queue: the dispute (with its transaction loaded), the two names the back office shows, and
 * whether the dispute is overdue at the moment the page was read.
 */
public record AgentDisputeSummaryView(
        Dispute dispute, String customerName, String assignedAgentName, boolean overdue) {}
