package com.clydehomu.disputes.dispute.domain;

/** Who caused a timeline event. SYSTEM is reserved for automated transitions and is not a login role. */
public enum ActorRole {
    CUSTOMER,
    AGENT,
    SYSTEM
}
