package com.clydehomu.disputes.agent.domain;

import com.clydehomu.disputes.dispute.domain.DisputeAction;
import java.util.EnumSet;
import java.util.Set;

/**
 * What the back-office UI can offer an agent. It is coarser than {@link DisputeAction}: the screen has one "resolve"
 * button, while the state machine distinguishes an upheld from a declined resolution.
 */
public enum AgentAction {
    CLAIM(DisputeAction.CLAIM),
    REQUEST_INFO(DisputeAction.REQUEST_INFO),
    RESOLVE(DisputeAction.RESOLVE_UPHELD, DisputeAction.RESOLVE_DECLINED);

    private final Set<DisputeAction> steps;

    AgentAction(DisputeAction first, DisputeAction... more) {
        this.steps = EnumSet.of(first, more);
    }

    /** Whether the state machine would accept at least one of the steps behind this button. */
    public boolean isOfferedBy(Set<DisputeAction> available) {
        return steps.stream().anyMatch(available::contains);
    }
}
