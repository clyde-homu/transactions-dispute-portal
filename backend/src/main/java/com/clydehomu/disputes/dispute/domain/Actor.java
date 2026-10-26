package com.clydehomu.disputes.dispute.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Who is asking for a transition. Agent and system actors carry no id in this version because no agent users exist; the
 * timeline still records the role.
 */
public record Actor(ActorRole role, UUID id) {

    public Actor {
        Objects.requireNonNull(role, "role");
    }

    public static Actor customer(UUID customerId) {
        return new Actor(ActorRole.CUSTOMER, Objects.requireNonNull(customerId, "customerId"));
    }

    public static Actor agent() {
        return new Actor(ActorRole.AGENT, null);
    }

    public static Actor system() {
        return new Actor(ActorRole.SYSTEM, null);
    }
}
