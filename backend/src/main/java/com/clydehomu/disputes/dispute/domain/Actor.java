package com.clydehomu.disputes.dispute.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Who is asking for a transition. Customers and agents are identified by their user id, which is how the state machine
 * checks ownership and assignment. System actors carry no id; the timeline still records the role.
 */
public record Actor(ActorRole role, UUID id) {

    public Actor {
        Objects.requireNonNull(role, "role");
    }

    public static Actor customer(UUID customerId) {
        return new Actor(ActorRole.CUSTOMER, Objects.requireNonNull(customerId, "customerId"));
    }

    public static Actor agent(UUID agentId) {
        return new Actor(ActorRole.AGENT, Objects.requireNonNull(agentId, "agentId"));
    }

    public static Actor system() {
        return new Actor(ActorRole.SYSTEM, null);
    }
}
