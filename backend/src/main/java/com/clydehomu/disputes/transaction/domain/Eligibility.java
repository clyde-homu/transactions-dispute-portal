package com.clydehomu.disputes.transaction.domain;

/** The answer to "can this transaction be disputed?", with a customer-facing explanation when the answer is no. */
public record Eligibility(boolean eligible, EligibilityReason reason, String message) {

    private static final Eligibility ELIGIBLE = new Eligibility(true, null, null);

    public static Eligibility allowed() {
        return ELIGIBLE;
    }

    public static Eligibility rejected(EligibilityReason reason, String message) {
        return new Eligibility(false, reason, message);
    }
}
