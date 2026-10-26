package com.clydehomu.disputes.agent.domain;

/** Which part of the work queue an agent is looking at. */
public enum AgentQueue {
    /** Nobody has claimed these yet. */
    UNASSIGNED,
    /** Assigned to the agent asking. */
    MINE,
    /** No assignment filter. */
    ALL
}
