package com.clydehomu.disputes.agent.domain;

import com.clydehomu.disputes.dispute.domain.DisputeAction;

/** How an agent decides a dispute. */
public enum ResolutionOutcome {
    UPHELD(DisputeAction.RESOLVE_UPHELD),
    DECLINED(DisputeAction.RESOLVE_DECLINED);

    private final DisputeAction action;

    ResolutionOutcome(DisputeAction action) {
        this.action = action;
    }

    public DisputeAction action() {
        return action;
    }
}
