package com.clydehomu.disputes.dispute.domain;

import static com.clydehomu.disputes.dispute.domain.ReasonCategory.AUTHORISATION;
import static com.clydehomu.disputes.dispute.domain.ReasonCategory.CONSUMER;
import static com.clydehomu.disputes.dispute.domain.ReasonCategory.FRAUD;
import static com.clydehomu.disputes.dispute.domain.ReasonCategory.PROCESSING_ERROR;

import java.time.Duration;
import java.time.Instant;

/**
 * The closed catalogue of reasons a customer can give. It is an enum rather than a table because the list changes with
 * the product, not with the data; the constant name is the code the API and the database use.
 *
 * <p>Each reason carries its service-level target in calendar days (ADR 0007): urgent for fraud and unauthorised
 * debits, longer for consumer disputes where the merchant has to respond.
 */
public enum DisputeReason {
    F01(FRAUD, "I didn't make this transaction", 10),
    F02(FRAUD, "My card was lost or stolen", 10),
    A01(AUTHORISATION, "I didn't authorise this debit order", 10),
    P01(PROCESSING_ERROR, "I was charged twice", 15),
    P02(PROCESSING_ERROR, "The amount is wrong", 15),
    C01(CONSUMER, "I didn't receive the goods or service", 30),
    C02(CONSUMER, "I cancelled, but I'm still being charged", 30),
    C03(CONSUMER, "My refund never arrived", 30);

    private final ReasonCategory category;
    private final String label;
    private final int slaDays;

    DisputeReason(ReasonCategory category, String label, int slaDays) {
        this.category = category;
        this.label = label;
        this.slaDays = slaDays;
    }

    public ReasonCategory category() {
        return category;
    }

    /** The customer's wording, shown in the stepper and on the dispute. */
    public String label() {
        return label;
    }

    /** Calendar days the bank aims to answer within. */
    public int slaDays() {
        return slaDays;
    }

    /**
     * When a dispute raised at {@code raisedAt} for this reason is due. A day is exactly 24 hours of UTC time, so the
     * result does not depend on any time zone (South Africa has no daylight saving).
     */
    public Instant dueAt(Instant raisedAt) {
        return raisedAt.plus(Duration.ofDays(slaDays));
    }
}
