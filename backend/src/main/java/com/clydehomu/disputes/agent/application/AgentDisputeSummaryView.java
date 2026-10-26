package com.clydehomu.disputes.agent.application;

import com.clydehomu.disputes.dispute.domain.Dispute;

/** One row of the work queue: the dispute (with its transaction loaded) and the two names the back office shows. */
public record AgentDisputeSummaryView(Dispute dispute, String customerName, String assignedAgentName) {}
