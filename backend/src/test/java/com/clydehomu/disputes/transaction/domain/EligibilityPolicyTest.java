package com.clydehomu.disputes.transaction.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EligibilityPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-26T07:00:00Z");

    private final EligibilityPolicy policy = new EligibilityPolicy(120);

    @Test
    void aRecentSettledDebitWithNoDisputeIsEligible() {
        Eligibility result = policy.evaluate(debit(TransactionStatus.SETTLED, daysAgo(3)), false, NOW);

        assertThat(result.eligible()).isTrue();
        assertThat(result.reason()).isNull();
        assertThat(result.message()).isNull();
    }

    @Test
    void aCreditIsNotADebit() {
        Eligibility result = policy.evaluate(
                transaction(TransactionDirection.CREDIT, TransactionStatus.SETTLED, daysAgo(3)), false, NOW);

        assertThat(result.eligible()).isFalse();
        assertThat(result.reason()).isEqualTo(EligibilityReason.NOT_A_DEBIT);
        assertThat(result.message()).isEqualTo("Only money that left your account can be disputed.");
    }

    @Test
    void aPendingDebitHasToClearFirst() {
        Eligibility result = policy.evaluate(debit(TransactionStatus.PENDING, daysAgo(0)), false, NOW);

        assertThat(result.eligible()).isFalse();
        assertThat(result.reason()).isEqualTo(EligibilityReason.PENDING);
        assertThat(result.message())
                .isEqualTo("This transaction is still pending. You can dispute it once it has cleared.");
    }

    @Test
    void aTransactionPostedExactlyOnTheWindowEdgeIsStillEligible() {
        Eligibility result = policy.evaluate(debit(TransactionStatus.SETTLED, daysAgo(120)), false, NOW);

        assertThat(result.eligible()).isTrue();
    }

    @Test
    void aTransactionOneSecondPastTheWindowIsTooOld() {
        Instant justOutside = NOW.minus(Duration.ofDays(120)).minusSeconds(1);

        Eligibility result = policy.evaluate(debit(TransactionStatus.SETTLED, justOutside), false, NOW);

        assertThat(result.eligible()).isFalse();
        assertThat(result.reason()).isEqualTo(EligibilityReason.TOO_OLD);
    }

    @Test
    void aTransactionOneHundredTwentyOneDaysOldIsTooOldAndTheMessageNamesTheWindow() {
        Eligibility result = policy.evaluate(debit(TransactionStatus.SETTLED, daysAgo(121)), false, NOW);

        assertThat(result.eligible()).isFalse();
        assertThat(result.reason()).isEqualTo(EligibilityReason.TOO_OLD);
        assertThat(result.message())
                .isEqualTo("This transaction is older than 120 days and can no longer be disputed.");
    }

    @Test
    void theWindowComesFromConfiguration() {
        EligibilityPolicy shortWindow = new EligibilityPolicy(30);

        assertThat(shortWindow
                        .evaluate(debit(TransactionStatus.SETTLED, daysAgo(30)), false, NOW)
                        .eligible())
                .isTrue();
        Eligibility tooOld = shortWindow.evaluate(debit(TransactionStatus.SETTLED, daysAgo(31)), false, NOW);
        assertThat(tooOld.reason()).isEqualTo(EligibilityReason.TOO_OLD);
        assertThat(tooOld.message()).contains("older than 30 days");
    }

    @Test
    void aTransactionThatAlreadyHasADisputeCannotBeDisputedAgain() {
        Eligibility result = policy.evaluate(debit(TransactionStatus.SETTLED, daysAgo(3)), true, NOW);

        assertThat(result.eligible()).isFalse();
        assertThat(result.reason()).isEqualTo(EligibilityReason.ALREADY_DISPUTED);
        assertThat(result.message()).isEqualTo("This transaction already has an open dispute.");
    }

    @Test
    void theFirstFailingRuleWinsInTheDocumentedOrder() {
        // Credit, pending, too old and already disputed all at once: the credit rule is checked first.
        assertThat(policy.evaluate(
                                transaction(TransactionDirection.CREDIT, TransactionStatus.PENDING, daysAgo(200)),
                                true,
                                NOW)
                        .reason())
                .isEqualTo(EligibilityReason.NOT_A_DEBIT);
        assertThat(policy.evaluate(debit(TransactionStatus.PENDING, daysAgo(200)), true, NOW)
                        .reason())
                .isEqualTo(EligibilityReason.PENDING);
        assertThat(policy.evaluate(debit(TransactionStatus.SETTLED, daysAgo(200)), true, NOW)
                        .reason())
                .isEqualTo(EligibilityReason.TOO_OLD);
    }

    @Test
    void aWindowShorterThanOneDayIsAConfigurationError() {
        assertThatThrownBy(() -> new EligibilityPolicy(0)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Instant daysAgo(int days) {
        return NOW.minus(Duration.ofDays(days));
    }

    private static BankTransaction debit(TransactionStatus status, Instant postedAt) {
        return transaction(TransactionDirection.DEBIT, status, postedAt);
    }

    private static BankTransaction transaction(
            TransactionDirection direction, TransactionStatus status, Instant postedAt) {
        return new BankTransaction(
                null,
                "TXN-20261026-000001",
                postedAt,
                new BigDecimal("100.00"),
                "ZAR",
                direction,
                status,
                "Fresh Market Midrand",
                "Groceries",
                Channel.CARD);
    }
}
