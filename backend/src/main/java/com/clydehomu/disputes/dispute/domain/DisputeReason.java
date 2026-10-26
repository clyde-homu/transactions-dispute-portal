package com.clydehomu.disputes.dispute.domain;

import static com.clydehomu.disputes.dispute.domain.ReasonCategory.AUTHORISATION;
import static com.clydehomu.disputes.dispute.domain.ReasonCategory.CONSUMER;
import static com.clydehomu.disputes.dispute.domain.ReasonCategory.FRAUD;
import static com.clydehomu.disputes.dispute.domain.ReasonCategory.PROCESSING_ERROR;

/**
 * The closed catalogue of reasons a customer can give. It is an enum rather than a table because the list changes with
 * the product, not with the data; the constant name is the code the API and the database use.
 */
public enum DisputeReason {
    F01(FRAUD, "I didn't make this transaction"),
    F02(FRAUD, "My card was lost or stolen"),
    A01(AUTHORISATION, "I didn't authorise this debit order"),
    P01(PROCESSING_ERROR, "I was charged twice"),
    P02(PROCESSING_ERROR, "The amount is wrong"),
    C01(CONSUMER, "I didn't receive the goods or service"),
    C02(CONSUMER, "I cancelled, but I'm still being charged"),
    C03(CONSUMER, "My refund never arrived");

    private final ReasonCategory category;
    private final String label;

    DisputeReason(ReasonCategory category, String label) {
        this.category = category;
        this.label = label;
    }

    public ReasonCategory category() {
        return category;
    }

    /** The customer's wording, shown in the stepper and on the dispute. */
    public String label() {
        return label;
    }
}
