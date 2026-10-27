package com.clydehomu.disputes.dispute.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.clydehomu.disputes.transaction.domain.BankTransaction;
import com.clydehomu.disputes.transaction.domain.Channel;
import com.clydehomu.disputes.transaction.domain.TransactionDirection;
import com.clydehomu.disputes.transaction.domain.TransactionStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** The service-level target of each reason, the due date fixed at creation, and the one definition of "overdue". */
class DisputeSlaTest {

    private static final Instant RAISED = Instant.parse("2026-10-20T08:15:30Z");
    private static final UUID CUSTOMER = UUID.fromString("6d0b8a51-3f2c-4e7a-9b14-0c5e2f7a9d31");
    private static final UUID AGENT_ID = UUID.fromString("3b9a7c52-1d4e-4f68-a0b3-7c8d9e0f1a2b");

    private final DisputeStateMachine machine = machineAt(RAISED);

    // ---- the catalogue ----

    @ParameterizedTest(name = "{0} has an SLA of {1} days")
    @CsvSource({"F01, 10", "F02, 10", "A01, 10", "P01, 15", "P02, 15", "C01, 30", "C02, 30", "C03, 30"})
    void eachReasonHasTheSlaFromTheCatalogue(DisputeReason reason, int days) {
        assertThat(reason.slaDays()).isEqualTo(days);
    }

    @Test
    void everyReasonIsCoveredByTheTable() {
        // Guards the table above: a ninth reason must be given an SLA on purpose.
        assertThat(DisputeReason.values())
                .hasSize(8)
                .allSatisfy(reason -> assertThat(reason.slaDays()).isPositive());
    }

    @ParameterizedTest
    @EnumSource(DisputeReason.class)
    void theDueDateIsWholeTwentyFourHourDaysAfterTheMoment(DisputeReason reason) {
        assertThat(reason.dueAt(RAISED)).isEqualTo(RAISED.plus(Duration.ofHours(24L * reason.slaDays())));
    }

    @Test
    void aDayIsTwentyFourHoursEvenAcrossADaylightSavingChangeElsewhere() {
        // 2026-03-29 is the clock change in Europe/London. UTC arithmetic ignores it, so the target stays 240 hours.
        Instant raised = Instant.parse("2026-03-25T10:00:00Z");

        assertThat(DisputeReason.F01.dueAt(raised)).isEqualTo(Instant.parse("2026-04-04T10:00:00Z"));
    }

    // ---- set at creation ----

    @ParameterizedTest
    @EnumSource(DisputeReason.class)
    void creationSetsTheDueDateFromTheReasonAndTheCreationTime(DisputeReason reason) {
        Dispute dispute = machine.create(request(reason), Actor.customer(CUSTOMER));

        assertThat(dispute.getCreatedAt()).isEqualTo(RAISED);
        assertThat(dispute.getDueAt()).isEqualTo(RAISED.plus(Duration.ofDays(reason.slaDays())));
    }

    @Test
    void theDueDateNeverMovesWhenTheDisputeIsWorked() {
        Dispute dispute = machine.create(request(DisputeReason.F01), Actor.customer(CUSTOMER));
        Instant promised = dispute.getDueAt();

        machineAt(RAISED.plus(Duration.ofDays(2))).apply(dispute, DisputeAction.CLAIM, Actor.agent(AGENT_ID), null);
        machineAt(RAISED.plus(Duration.ofDays(3)))
                .apply(dispute, DisputeAction.REQUEST_INFO, Actor.agent(AGENT_ID), "Please send the invoice.");
        machineAt(RAISED.plus(Duration.ofDays(30)))
                .apply(dispute, DisputeAction.CUSTOMER_RESPOND, Actor.customer(CUSTOMER), "Here it is.");

        assertThat(dispute.getDueAt()).isEqualTo(promised);
    }

    // ---- overdue ----

    @Test
    void aDisputeDueExactlyNowIsNotYetOverdueButOneSecondLaterItIs() {
        Dispute dispute = machine.create(request(DisputeReason.P02), Actor.customer(CUSTOMER));
        Instant due = dispute.getDueAt();

        assertThat(dispute.isOverdue(due.minusSeconds(1))).isFalse();
        assertThat(dispute.isOverdue(due)).as("exactly due is not late").isFalse();
        assertThat(dispute.isOverdue(due.plusSeconds(1))).as("one second after").isTrue();
        assertThat(dispute.isOverdue(due.plusNanos(1)))
                .as("strictly after, however little")
                .isTrue();
        assertThat(dispute.isOverdue(due.plus(Duration.ofDays(365)))).isTrue();
    }

    @ParameterizedTest
    @EnumSource(
            value = DisputeStatus.class,
            names = {"SUBMITTED", "UNDER_REVIEW", "AWAITING_CUSTOMER_INFO"})
    void anOpenDisputePastItsDueDateIsOverdueInEveryOpenStatus(DisputeStatus status) {
        Dispute dispute = disputeIn(status);

        assertThat(dispute.isOverdue(dispute.getDueAt().plusSeconds(1))).isTrue();
        assertThat(dispute.isOverdue(dispute.getDueAt())).isFalse();
    }

    @ParameterizedTest
    @EnumSource(
            value = DisputeStatus.class,
            names = {"RESOLVED_UPHELD", "RESOLVED_DECLINED", "WITHDRAWN"})
    void aClosedDisputeIsNeverOverdueHoweverLateItWasClosedOrHowLongAgo(DisputeStatus status) {
        Dispute dispute = disputeIn(status);

        assertThat(dispute.isOverdue(dispute.getDueAt().plusSeconds(1))).isFalse();
        assertThat(dispute.isOverdue(dispute.getDueAt().plus(Duration.ofDays(400))))
                .isFalse();
    }

    @Test
    void closingADisputeAfterItsDueDateEndsTheLateness() {
        Dispute dispute = machine.create(request(DisputeReason.F01), Actor.customer(CUSTOMER));
        machineAt(RAISED.plus(Duration.ofDays(1))).apply(dispute, DisputeAction.CLAIM, Actor.agent(AGENT_ID), null);
        Instant eleventhDay = RAISED.plus(Duration.ofDays(11));
        assertThat(dispute.isOverdue(eleventhDay)).isTrue();

        machineAt(eleventhDay)
                .apply(dispute, DisputeAction.RESOLVE_UPHELD, Actor.agent(AGENT_ID), "Upheld.", "Checked late.");

        assertThat(dispute.isOverdue(eleventhDay)).isFalse();
    }

    @Test
    void theOpenStatusesTheQueryFiltersOnAreExactlyTheOnesThatCanBeOverdue() {
        EnumSet<DisputeStatus> canBeOverdue = EnumSet.noneOf(DisputeStatus.class);
        for (DisputeStatus status : DisputeStatus.values()) {
            if (disputeIn(status).isOverdue(RAISED.plus(Duration.ofDays(400)))) {
                canBeOverdue.add(status);
            }
        }

        assertThat(DisputeStatus.openStatuses()).containsExactlyInAnyOrderElementsOf(canBeOverdue);
    }

    // ---- helpers ----

    private static DisputeStateMachine machineAt(Instant at) {
        return new DisputeStateMachine(Clock.fixed(at, ZoneOffset.UTC));
    }

    /** Walks a new dispute to the wanted status using only legal moves, all within a day of being raised. */
    private Dispute disputeIn(DisputeStatus target) {
        Actor owner = Actor.customer(CUSTOMER);
        Actor agent = Actor.agent(AGENT_ID);
        Dispute dispute = machine.create(request(DisputeReason.P02), owner);
        switch (target) {
            case SUBMITTED -> {}
            case UNDER_REVIEW -> machine.apply(dispute, DisputeAction.CLAIM, agent, null);
            case AWAITING_CUSTOMER_INFO -> {
                machine.apply(dispute, DisputeAction.CLAIM, agent, null);
                machine.apply(dispute, DisputeAction.REQUEST_INFO, agent, "Please send the invoice.");
            }
            case RESOLVED_UPHELD -> {
                machine.apply(dispute, DisputeAction.CLAIM, agent, null);
                machine.apply(dispute, DisputeAction.RESOLVE_UPHELD, agent, "Upheld.", "Note.");
            }
            case RESOLVED_DECLINED -> {
                machine.apply(dispute, DisputeAction.CLAIM, agent, null);
                machine.apply(dispute, DisputeAction.RESOLVE_DECLINED, agent, "Declined.", "Note.");
            }
            case WITHDRAWN -> machine.apply(dispute, DisputeAction.WITHDRAW, owner, null);
        }
        return dispute;
    }

    private static NewDispute request(DisputeReason reason) {
        BankTransaction transaction = new BankTransaction(
                null,
                "TXN-20261019-000151",
                Instant.parse("2026-10-19T11:02:48Z"),
                new BigDecimal("1250.0000"),
                "ZAR",
                TransactionDirection.DEBIT,
                TransactionStatus.SETTLED,
                "Metro Fuel N1",
                "Fuel",
                Channel.CARD);
        return new NewDispute(
                transaction,
                CUSTOMER,
                reason,
                new BigDecimal("250.00"),
                "The pump charged me R1250 for R1000 of fuel.",
                "DSP-2026-000001");
    }
}
