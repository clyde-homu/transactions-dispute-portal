package com.clydehomu.disputes.dispute.domain;

import com.clydehomu.disputes.shared.error.ApiException;
import com.clydehomu.disputes.shared.error.ErrorCode;

/** The state machine refused a move: the dispute is in the wrong status, or the actor may not make that move. */
public class InvalidDisputeTransitionException extends ApiException {

    public InvalidDisputeTransitionException(String message) {
        super(ErrorCode.INVALID_DISPUTE_TRANSITION, message);
    }
}
