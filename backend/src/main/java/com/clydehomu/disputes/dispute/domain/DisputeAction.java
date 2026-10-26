package com.clydehomu.disputes.dispute.domain;

/**
 * The events that can move a dispute. Each one is performed by exactly one kind of actor and some of them must explain
 * themselves to the customer or leave a note for the back office, so those facts live next to the name rather than in
 * the transition table.
 */
public enum DisputeAction {
    CREATE(ActorRole.CUSTOMER, false, false, "raise", "created"),
    CLAIM(ActorRole.AGENT, false, false, "claim", "claimed"),
    REQUEST_INFO(ActorRole.AGENT, true, false, "request information on", "sent back for more information"),
    CUSTOMER_RESPOND(ActorRole.CUSTOMER, true, false, "respond to", "answered"),
    RESOLVE_UPHELD(ActorRole.AGENT, true, true, "uphold", "upheld"),
    RESOLVE_DECLINED(ActorRole.AGENT, true, true, "decline", "declined"),
    WITHDRAW(ActorRole.CUSTOMER, false, false, "withdraw", "withdrawn");

    private final ActorRole actor;
    private final boolean messageRequired;
    private final boolean internalNoteRequired;
    private final String verb;
    private final String pastParticiple;

    DisputeAction(
            ActorRole actor,
            boolean messageRequired,
            boolean internalNoteRequired,
            String verb,
            String pastParticiple) {
        this.actor = actor;
        this.messageRequired = messageRequired;
        this.internalNoteRequired = internalNoteRequired;
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

    /** Whether the agent must leave a back-office note at this step (customers never see it). */
    public boolean internalNoteRequired() {
        return internalNoteRequired;
    }

    /** Agent steps other than the claim belong to the agent the dispute is assigned to. */
    public boolean requiresAssignedAgent() {
        return actor == ActorRole.AGENT && this != CLAIM;
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
