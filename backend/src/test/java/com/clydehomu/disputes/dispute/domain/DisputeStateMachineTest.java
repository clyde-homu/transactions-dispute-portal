package com.clydehomu.disputes.dispute.domain;

import static com.clydehomu.disputes.dispute.domain.DisputeAction.CLAIM;
import static com.clydehomu.disputes.dispute.domain.DisputeAction.CREATE;
import static com.clydehomu.disputes.dispute.domain.DisputeAction.CUSTOMER_RESPOND;
import static com.clydehomu.disputes.dispute.domain.DisputeAction.REQUEST_INFO;
import static com.clydehomu.disputes.dispute.domain.DisputeAction.RESOLVE_DECLINED;
import static com.clydehomu.disputes.dispute.domain.DisputeAction.RESOLVE_UPHELD;
import static com.clydehomu.disputes.dispute.domain.DisputeAction.WITHDRAW;
import static com.clydehomu.disputes.dispute.domain.DisputeStatus.AWAITING_CUSTOMER_INFO;
import static com.clydehomu.disputes.dispute.domain.DisputeStatus.SUBMITTED;
import static com.clydehomu.disputes.dispute.domain.DisputeStatus.UNDER_REVIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.clydehomu.disputes.shared.error.ErrorCode;
import com.clydehomu.disputes.shared.error.ValidationFailedException;
import com.clydehomu.disputes.transaction.domain.BankTransaction;
import com.clydehomu.disputes.transaction.domain.Channel;
import com.clydehomu.disputes.transaction.domain.TransactionDirection;
import com.clydehomu.disputes.transaction.domain.TransactionStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class DisputeStateMachineTest {

    private static final Instant NOW = Instant.parse("2026-10-26T07:00:00Z");
    private static final UUID CUSTOMER = UUID.fromString("6d0b8a51-3f2c-4e7a-9b14-0c5e2f7a9d31");
    private static final UUID OTHER_CUSTOMER = UUID.fromString("0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");

    private static final Actor OWNER = Actor.customer(CUSTOMER);
    private static final Actor AGENT = Actor.agent();

    /** Every legal move, straight from the transition table in docs/domain.md. */
    private static final Map<DisputeAction, Map<DisputeStatus, DisputeStatus>> LEGAL = legalMoves();

    private final DisputeStateMachine machine = machineAt(NOW);

    // ---- create ----

    @Test
    void createOpensASubmittedDisputeWithItsCreationEvent() {
        Dispute dispute = machine.create(request(), OWNER);

        assertThat(dispute.getStatus()).isEqualTo(SUBMITTED);
        assertThat(dispute.getReference()).isEqualTo("DSP-2026-000001");
        assertThat(dispute.getCustomerId()).isEqualTo(CUSTOMER);
        assertThat(dispute.getReason()).isEqualTo(DisputeReason.P02);
        assertThat(dispute.getDisputedAmount()).isEqualByComparingTo("250.00");
        assertThat(dispute.getCurrency()).isEqualTo("ZAR");
        assertThat(dispute.getDescription()).isEqualTo("The pump charged me R1250 for R1000 of fuel.");
        assertThat(dispute.getCreatedAt()).isEqualTo(NOW);
        assertThat(dispute.getUpdatedAt()).isEqualTo(NOW);
        assertThat(dispute.getResolvedAt()).isNull();
        assertThat(dispute.getEvents()).singleElement().satisfies(event -> {
            assertThat(event.getFromStatus()).isNull();
            assertThat(event.getToStatus()).isEqualTo(SUBMITTED);
            assertThat(event.getActorRole()).isEqualTo(ActorRole.CUSTOMER);
            assertThat(event.getActorId()).isEqualTo(CUSTOMER);
            assertThat(event.getMessage()).isNull();
            assertThat(event.getCreatedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void onlyTheOwningCustomerCanCreateADispute() {
        assertRejected(() -> machine.create(request(), AGENT));
        assertRejected(() -> machine.create(request(), Actor.system()));
        assertRejected(() -> machine.create(request(), Actor.customer(OTHER_CUSTOMER)));
    }

    @Test
    void createCannotBeUsedOnAnExistingDispute() {
        Dispute dispute = machine.create(request(), OWNER);

        assertRejected(() -> machine.apply(dispute, CREATE, OWNER, null));
    }

    // ---- every legal transition ----

    static Stream<Arguments> legalTransitions() {
        return LEGAL.entrySet().stream()
                .flatMap(byAction -> byAction.getValue().entrySet().stream()
                        .map(move -> Arguments.of(byAction.getKey(), move.getKey(), move.getValue())));
    }

    @ParameterizedTest(name = "{1} --{0}--> {2}")
    @MethodSource("legalTransitions")
    void everyLegalTransitionMovesTheStatusAndAppendsOneEvent(
            DisputeAction action, DisputeStatus from, DisputeStatus to) {
        Dispute dispute = disputeIn(from);
        int eventsBefore = dispute.getEvents().size();
        Instant later = NOW.plus(Duration.ofHours(5));
        Actor actor = actorFor(action);

        DisputeEvent event = machineAt(later).apply(dispute, action, actor, messageFor(action));

        assertThat(dispute.getStatus()).isEqualTo(to);
        assertThat(dispute.getUpdatedAt()).isEqualTo(later);
        assertThat(dispute.getCreatedAt()).as("creation time never changes").isEqualTo(NOW);
        assertThat(dispute.getResolvedAt()).isEqualTo(to.isClosed() ? later : null);
        assertThat(dispute.getEvents()).hasSize(eventsBefore + 1).last().isSameAs(event);
        assertThat(event.getFromStatus()).isEqualTo(from);
        assertThat(event.getToStatus()).isEqualTo(to);
        assertThat(event.getActorRole()).isEqualTo(action.actor());
        assertThat(event.getActorId()).isEqualTo(actor.id());
        assertThat(event.getMessage()).isEqualTo(messageFor(action));
        assertThat(event.getCreatedAt()).isEqualTo(later);
    }

    @Test
    void theTableHasTheEightMovesOnAnExistingDisputePlusCreation() {
        assertThat(legalTransitions().count()).isEqualTo(8);
    }

    // ---- every illegal transition ----

    static Stream<Arguments> everyStatusAndAction() {
        return Stream.of(DisputeStatus.values())
                .flatMap(status -> Stream.of(DisputeAction.values()).map(action -> Arguments.of(status, action)));
    }

    @ParameterizedTest(name = "{0} + {1}")
    @MethodSource("everyStatusAndAction")
    void aMoveNotInTheTableIsRejectedAndChangesNothing(DisputeStatus from, DisputeAction action) {
        if (isLegal(from, action)) {
            return;
        }
        Dispute dispute = disputeIn(from);
        int eventsBefore = dispute.getEvents().size();
        Instant updatedBefore = dispute.getUpdatedAt();

        assertThatThrownBy(() -> machineAt(NOW.plusSeconds(60)).apply(dispute, action, actorFor(action), "A message"))
                .isInstanceOfSatisfying(InvalidDisputeTransitionException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_DISPUTE_TRANSITION);
                    assertThat(e.getStatus().value()).isEqualTo(409);
                });

        assertThat(dispute.getStatus()).isEqualTo(from);
        assertThat(dispute.getEvents()).hasSize(eventsBefore);
        assertThat(dispute.getUpdatedAt()).isEqualTo(updatedBefore);
    }

    @ParameterizedTest
    @EnumSource(
            value = DisputeStatus.class,
            names = {"RESOLVED_UPHELD", "RESOLVED_DECLINED", "WITHDRAWN"})
    void closedDisputesAcceptNoFurtherMoves(DisputeStatus closed) {
        Dispute dispute = disputeIn(closed);

        for (DisputeAction action : DisputeAction.values()) {
            assertRejected(() -> machine.apply(dispute, action, actorFor(action), "A message"));
        }
        assertThat(dispute.getStatus()).isEqualTo(closed);
    }

    @Test
    void theRejectionNamesTheStatusAndTheAttemptedAction() {
        Dispute declined = disputeIn(DisputeStatus.RESOLVED_DECLINED);

        assertThatThrownBy(() -> machine.apply(declined, WITHDRAW, OWNER, null))
                .hasMessage("A dispute in status RESOLVED_DECLINED cannot be withdrawn.");
    }

    // ---- actor guards ----

    @ParameterizedTest(name = "{1} by the wrong role in {0}")
    @MethodSource("everyStatusAndAction")
    void aLegalMoveByTheWrongRoleIsRejectedAndChangesNothing(DisputeStatus from, DisputeAction action) {
        if (!isLegal(from, action)) {
            return;
        }
        Dispute dispute = disputeIn(from);
        int eventsBefore = dispute.getEvents().size();

        for (Actor wrong : List.of(wrongRole(action), Actor.system())) {
            assertRejected(() -> machine.apply(dispute, action, wrong, "A message"));
        }

        assertThat(dispute.getStatus()).isEqualTo(from);
        assertThat(dispute.getEvents()).hasSize(eventsBefore);
    }

    @Test
    void aCustomerCannotWorkTheBackOfficeSteps() {
        assertRejected(() -> machine.apply(disputeIn(SUBMITTED), CLAIM, OWNER, null));
        assertRejected(() -> machine.apply(disputeIn(UNDER_REVIEW), REQUEST_INFO, OWNER, "More please"));
        assertRejected(() -> machine.apply(disputeIn(UNDER_REVIEW), RESOLVE_UPHELD, OWNER, "Done"));
        assertRejected(() -> machine.apply(disputeIn(UNDER_REVIEW), RESOLVE_DECLINED, OWNER, "Done"));
    }

    @Test
    void anAgentCannotWithdrawOrAnswerOnTheCustomersBehalf() {
        assertRejected(() -> machine.apply(disputeIn(SUBMITTED), WITHDRAW, AGENT, null));
        assertRejected(() -> machine.apply(disputeIn(AWAITING_CUSTOMER_INFO), CUSTOMER_RESPOND, AGENT, "Here"));
    }

    @Test
    void aDifferentCustomerCannotWithdrawSomeoneElsesDispute() {
        Dispute dispute = disputeIn(SUBMITTED);

        assertThatThrownBy(() -> machine.apply(dispute, WITHDRAW, Actor.customer(OTHER_CUSTOMER), null))
                .isInstanceOf(InvalidDisputeTransitionException.class);
        assertThat(dispute.getStatus()).isEqualTo(SUBMITTED);
    }

    @Test
    void aCustomerActorWithoutAnIdIsNotTheOwner() {
        assertRejected(() -> machine.apply(disputeIn(SUBMITTED), WITHDRAW, new Actor(ActorRole.CUSTOMER, null), null));
    }

    @Test
    void agentEventsCarryTheRoleAndNoActorId() {
        Dispute dispute = disputeIn(SUBMITTED);

        DisputeEvent event = machine.apply(dispute, CLAIM, AGENT, "We have picked this up.");

        assertThat(event.getActorRole()).isEqualTo(ActorRole.AGENT);
        assertThat(event.getActorId()).isNull();
    }

    // ---- messages ----

    @ParameterizedTest
    @EnumSource(
            value = DisputeAction.class,
            names = {"REQUEST_INFO", "CUSTOMER_RESPOND", "RESOLVE_UPHELD", "RESOLVE_DECLINED"})
    void stepsThatMustExplainThemselvesRejectAMissingMessage(DisputeAction action) {
        DisputeStatus from = LEGAL.get(action).keySet().iterator().next();
        Dispute dispute = disputeIn(from);

        for (String missing : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> machine.apply(dispute, action, actorFor(action), missing))
                    .isInstanceOfSatisfying(
                            ValidationFailedException.class,
                            e -> assertThat(e.getViolations()).singleElement().satisfies(v -> {
                                assertThat(v.field()).isEqualTo("message");
                                assertThat(v.message()).isEqualTo("is required");
                            }));
        }
        assertThat(dispute.getStatus()).isEqualTo(from);
    }

    @Test
    void aMessageOverOneThousandCharactersIsRejectedButExactlyOneThousandIsKept() {
        Dispute dispute = disputeIn(UNDER_REVIEW);

        assertThatThrownBy(() -> machine.apply(dispute, RESOLVE_DECLINED, AGENT, "x".repeat(1001)))
                .isInstanceOf(ValidationFailedException.class);
        assertThat(dispute.getStatus()).isEqualTo(UNDER_REVIEW);

        DisputeEvent event = machine.apply(dispute, RESOLVE_DECLINED, AGENT, "x".repeat(1000));
        assertThat(event.getMessage()).hasSize(1000);
    }

    @Test
    void messagesAreTrimmedAndAnOptionalBlankMessageBecomesNull() {
        Dispute dispute = disputeIn(SUBMITTED);

        DisputeEvent claim = machine.apply(dispute, CLAIM, AGENT, "  Picked up.  ");
        DisputeEvent withdraw = machine.apply(dispute, WITHDRAW, OWNER, "   ");

        assertThat(claim.getMessage()).isEqualTo("Picked up.");
        assertThat(withdraw.getMessage()).isNull();
    }

    // ---- the whole journey ----

    @Test
    void aFullJourneyLeavesAnOrderedTrailAndClosesTheDispute() {
        Dispute dispute = machineAt(NOW).create(request(), OWNER);
        machineAt(NOW.plus(Duration.ofHours(2))).apply(dispute, CLAIM, AGENT, null);
        machineAt(NOW.plus(Duration.ofDays(1))).apply(dispute, REQUEST_INFO, AGENT, "Please send the invoice.");
        machineAt(NOW.plus(Duration.ofDays(2))).apply(dispute, CUSTOMER_RESPOND, OWNER, "Invoice attached.");
        machineAt(NOW.plus(Duration.ofDays(3))).apply(dispute, RESOLVE_UPHELD, AGENT, "Your dispute is upheld.");

        assertThat(dispute.getEvents())
                .extracting(DisputeEvent::getFromStatus, DisputeEvent::getToStatus, DisputeEvent::getActorRole)
                .containsExactly(
                        tuple(null, SUBMITTED, ActorRole.CUSTOMER),
                        tuple(SUBMITTED, UNDER_REVIEW, ActorRole.AGENT),
                        tuple(UNDER_REVIEW, AWAITING_CUSTOMER_INFO, ActorRole.AGENT),
                        tuple(AWAITING_CUSTOMER_INFO, UNDER_REVIEW, ActorRole.CUSTOMER),
                        tuple(UNDER_REVIEW, DisputeStatus.RESOLVED_UPHELD, ActorRole.AGENT));
        assertThat(dispute.getEvents()).extracting(DisputeEvent::getCreatedAt).isSorted();
        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.RESOLVED_UPHELD);
        assertThat(dispute.getCreatedAt()).isEqualTo(NOW);
        assertThat(dispute.getUpdatedAt()).isEqualTo(NOW.plus(Duration.ofDays(3)));
        assertThat(dispute.getResolvedAt()).isEqualTo(NOW.plus(Duration.ofDays(3)));
    }

    // ---- the table and the API's canWithdraw ----

    @ParameterizedTest
    @EnumSource(DisputeStatus.class)
    void aDisputeCanBeWithdrawnExactlyWhenItIsOpen(DisputeStatus status) {
        assertThat(machine.allows(status, WITHDRAW)).isEqualTo(status.isOpen());
        assertThat(status.isClosed()).isEqualTo(!status.isOpen());
    }

    @ParameterizedTest(name = "allows({0}, {1}) matches the table")
    @MethodSource("everyStatusAndAction")
    void allowsReportsExactlyTheLegalMoves(DisputeStatus from, DisputeAction action) {
        assertThat(machine.allows(from, action)).isEqualTo(isLegal(from, action));
    }

    // ---- helpers ----

    private static Map<DisputeAction, Map<DisputeStatus, DisputeStatus>> legalMoves() {
        Map<DisputeAction, Map<DisputeStatus, DisputeStatus>> moves = new EnumMap<>(DisputeAction.class);
        moves.put(CLAIM, Map.of(SUBMITTED, UNDER_REVIEW));
        moves.put(REQUEST_INFO, Map.of(UNDER_REVIEW, AWAITING_CUSTOMER_INFO));
        moves.put(CUSTOMER_RESPOND, Map.of(AWAITING_CUSTOMER_INFO, UNDER_REVIEW));
        moves.put(RESOLVE_UPHELD, Map.of(UNDER_REVIEW, DisputeStatus.RESOLVED_UPHELD));
        moves.put(RESOLVE_DECLINED, Map.of(UNDER_REVIEW, DisputeStatus.RESOLVED_DECLINED));
        moves.put(
                WITHDRAW,
                Map.of(
                        SUBMITTED, DisputeStatus.WITHDRAWN,
                        UNDER_REVIEW, DisputeStatus.WITHDRAWN,
                        AWAITING_CUSTOMER_INFO, DisputeStatus.WITHDRAWN));
        return moves;
    }

    private static boolean isLegal(DisputeStatus from, DisputeAction action) {
        return LEGAL.getOrDefault(action, Map.of()).containsKey(from);
    }

    private static Actor actorFor(DisputeAction action) {
        return action.actor() == ActorRole.CUSTOMER ? OWNER : AGENT;
    }

    private static Actor wrongRole(DisputeAction action) {
        return action.actor() == ActorRole.CUSTOMER ? AGENT : OWNER;
    }

    private static String messageFor(DisputeAction action) {
        return action.messageRequired() ? "A message for the customer." : null;
    }

    private static DisputeStateMachine machineAt(Instant at) {
        return new DisputeStateMachine(Clock.fixed(at, ZoneOffset.UTC));
    }

    private static void assertRejected(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(InvalidDisputeTransitionException.class);
    }

    /** Walks a new dispute to the wanted status using only legal moves, so the setup cannot cheat. */
    private Dispute disputeIn(DisputeStatus target) {
        Dispute dispute = machine.create(request(), OWNER);
        switch (target) {
            case SUBMITTED -> {}
            case UNDER_REVIEW -> machine.apply(dispute, CLAIM, AGENT, null);
            case AWAITING_CUSTOMER_INFO -> {
                machine.apply(dispute, CLAIM, AGENT, null);
                machine.apply(dispute, REQUEST_INFO, AGENT, "Please send the invoice.");
            }
            case RESOLVED_UPHELD -> {
                machine.apply(dispute, CLAIM, AGENT, null);
                machine.apply(dispute, RESOLVE_UPHELD, AGENT, "Upheld.");
            }
            case RESOLVED_DECLINED -> {
                machine.apply(dispute, CLAIM, AGENT, null);
                machine.apply(dispute, RESOLVE_DECLINED, AGENT, "Declined.");
            }
            case WITHDRAWN -> machine.apply(dispute, WITHDRAW, OWNER, null);
        }
        return dispute;
    }

    private static NewDispute request() {
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
                DisputeReason.P02,
                new BigDecimal("250.00"),
                "The pump charged me R1250 for R1000 of fuel.",
                "DSP-2026-000001");
    }
}
