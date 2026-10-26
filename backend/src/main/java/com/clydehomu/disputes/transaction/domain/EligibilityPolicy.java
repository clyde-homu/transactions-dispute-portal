package com.clydehomu.disputes.transaction.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * The one place that decides whether a transaction can be disputed. Rules run in a fixed order and the first failure
 * wins, so the customer always sees the most fundamental reason. The transaction detail endpoint shows the result,
 * and dispute creation enforces it.
 */
public final class EligibilityPolicy {

    private final int windowDays;

    public EligibilityPolicy(int windowDays) {
        if (windowDays < 1) {
            throw new IllegalArgumentException("The dispute window must be at least one day");
        }
        this.windowDays = windowDays;
    }

    /**
     * @param hasActiveDispute whether the transaction already has a dispute that is not withdrawn
     * @param now the current time; a transaction posted exactly {@code windowDays} ago is still eligible
     */
    public Eligibility evaluate(BankTransaction transaction, boolean hasActiveDispute, Instant now) {
        if (transaction.getDirection() != TransactionDirection.DEBIT) {
            return Eligibility.rejected(
                    EligibilityReason.NOT_A_DEBIT, "Only money that left your account can be disputed.");
        }
        if (transaction.getStatus() != TransactionStatus.SETTLED) {
            return Eligibility.rejected(
                    EligibilityReason.PENDING,
                    "This transaction is still pending. You can dispute it once it has cleared.");
        }
        if (transaction.getPostedAt().isBefore(now.minus(Duration.ofDays(windowDays)))) {
            return Eligibility.rejected(
                    EligibilityReason.TOO_OLD,
                    "This transaction is older than " + windowDays + " days and can no longer be disputed.");
        }
        if (hasActiveDispute) {
            return Eligibility.rejected(
                    EligibilityReason.ALREADY_DISPUTED, "This transaction already has an open dispute.");
        }
        return Eligibility.allowed();
    }
}
