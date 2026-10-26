package com.clydehomu.disputes.transaction.api;

import com.clydehomu.disputes.transaction.domain.Eligibility;
import com.clydehomu.disputes.transaction.domain.EligibilityReason;

public record EligibilityResponse(boolean eligible, EligibilityReason reasonCode, String message) {

    static EligibilityResponse from(Eligibility eligibility) {
        return new EligibilityResponse(eligibility.eligible(), eligibility.reason(), eligibility.message());
    }
}
