package com.clydehomu.disputes.agent.domain;

/** How the work queue is ordered. Both orders end on the dispute id, so a page boundary never repeats or skips a row. */
public enum AgentQueueSort {
    /** Due soonest first, then oldest, so late and urgent work comes to the top (ADR 0007). The default. */
    DUE_AT,
    /** First in, first out. */
    CREATED_AT
}
