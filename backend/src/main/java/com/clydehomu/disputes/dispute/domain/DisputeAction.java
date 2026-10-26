package com.clydehomu.disputes.dispute.domain;

/**
 * The events that can move a dispute. Each one is performed by exactly one kind of actor and some of them must explain
 * themselves to the customer, so both facts live next to the name rather than in the transition table.
 */
public enum DisputeAction {
    CREATE(ActorRole.CUSTOMER, false, "raise", "created"),
    CLAIM(ActorRole.AGENT, false, "claim", "claimed"),
    REQUEST_INFO(ActorRole.AGENT, true, "request information on", "sent back for more information"),
    CUSTOMER_RESPOND(ActorRole.CUSTOMER, true, "respond to", "answered"),
    RESOLVE_UPHELD(ActorRole.AGENT, true, "uphold", "upheld"),
    RESOLVE_DECLINED(ActorRole.AGENT, true, "decline", "declined"),
    WITHDRAW(ActorRole.CUSTOMER, false, "withdraw", "withdrawn");

    private final ActorRole actor;
    private final boolean messageRequired;
    private final String verb;
    private final String pastParticiple;

    DisputeAction(ActorRole actor, boolean messageRequired, String verb, String pastParticiple) {
        this.actor = actor;
        this.messageRequired = messageRequired;
        this.verb = verb;
        this.pastParticiple = pastParticiple;
    }

    public ActorRole actor() {
        return actor;
    }

    /** Whether the customer must be told something at this step (the timeline shows it). */
    public boolean messageRequired() {
        return messageRequired;
    }

    /** As in "The customer role cannot {verb} a dispute." */
    String verb() {
        return verb;
    }

    /** As in "A dispute in status X cannot be {pastParticiple}." */
    String pastParticiple() {
        return pastParticiple;
    }
}
