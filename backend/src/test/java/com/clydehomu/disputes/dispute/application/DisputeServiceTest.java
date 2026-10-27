package com.clydehomu.disputes.dispute.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.clydehomu.disputes.dispute.domain.Actor;
import com.clydehomu.disputes.dispute.domain.Dispute;
import com.clydehomu.disputes.dispute.domain.DisputeAction;
import com.clydehomu.disputes.dispute.domain.DisputeEvent;
import com.clydehomu.disputes.dispute.domain.DisputeReason;
import com.clydehomu.disputes.dispute.domain.DisputeStateMachine;
import com.clydehomu.disputes.dispute.domain.DisputeStatus;
import com.clydehomu.disputes.dispute.domain.InvalidDisputeTransitionException;
import com.clydehomu.disputes.dispute.domain.NewDispute;
import com.clydehomu.disputes.dispute.infrastructure.DisputeRepository;
import com.clydehomu.disputes.shared.error.ApiException;
import com.clydehomu.disputes.shared.error.ErrorCode;
import com.clydehomu.disputes.shared.error.NotFoundException;
import com.clydehomu.disputes.shared.error.ValidationFailedException;
import com.clydehomu.disputes.transaction.domain.BankTransaction;
import com.clydehomu.disputes.transaction.domain.Channel;
import com.clydehomu.disputes.transaction.domain.EligibilityPolicy;
import com.clydehomu.disputes.transaction.domain.TransactionDirection;
import com.clydehomu.disputes.transaction.domain.TransactionStatus;
import com.clydehomu.disputes.transaction.infrastructure.BankTransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

class DisputeServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-26T07:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID CUSTOMER = UUID.fromString("6d0b8a51-3f2c-4e7a-9b14-0c5e2f7a9d31");
    private static final UUID TRANSACTION_ID = UUID.fromString("5e8a1c3d-7b2f-4a9e-8c6d-0f1e2a3b4c5d");
    private static final UUID DISPUTE_ID = UUID.fromString("0f9e8d7c-6b5a-4c3d-8e2f-1a0b9c8d7e6f");
    private static final UUID AGENT = UUID.fromString("3b9a7c52-1d4e-4f68-a0b3-7c8d9e0f1a2b");

    private final DisputeRepository disputes = mock(DisputeRepository.class);
    private final BankTransactionRepository transactions = mock(BankTransactionRepository.class);
    private final DisputeStateMachine stateMachine = new DisputeStateMachine(CLOCK);
    private DisputeService service;

    @BeforeEach
    void setUp() {
        service = new DisputeService(disputes, transactions, new EligibilityPolicy(120), stateMachine, CLOCK);
        when(disputes.save(any(Dispute.class))).thenAnswer(call -> call.getArgument(0));
        when(disputes.nextReferenceNumber()).thenReturn(42L);
        when(disputes.findActiveReference(any())).thenReturn(Optional.empty());
        when(disputes.findActiveStatus(any())).thenReturn(Optional.empty());
    }

    // ---- create ----

    @Test
    void createSavesASubmittedDisputeWithTheNextReferenceAndOneEvent() {
        BankTransaction fuel = transaction("1250.00", TransactionStatus.SETTLED, TransactionDirection.DEBIT, 3);
        when(transactions.findOwnedByIdForUpdate(TRANSACTION_ID, CUSTOMER)).thenReturn(Optional.of(fuel));

        DisputeView view = service.create(CUSTOMER, command("250.00"));

        ArgumentCaptor<Dispute> saved = ArgumentCaptor.forClass(Dispute.class);
        verify(disputes).save(saved.capture());
        Dispute dispute = saved.getValue();
        assertThat(view.dispute()).isSameAs(dispute);
        assertThat(view.transactionDisputeStatus()).isEqualTo(DisputeStatus.SUBMITTED);
        assertThat(dispute.getReference()).isEqualTo("DSP-2026-000042");
        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.SUBMITTED);
        assertThat(dispute.getCustomerId()).isEqualTo(CUSTOMER);
        assertThat(dispute.getTransaction()).isSameAs(fuel);
        assertThat(dispute.getDisputedAmount()).isEqualTo(new BigDecimal("250.00"));
        assertThat(dispute.getCurrency()).isEqualTo("ZAR");
        assertThat(dispute.getEvents())
                .singleElement()
                .satisfies(event -> assertThat(event.getActorId()).isEqualTo(CUSTOMER));
    }

    @Test
    void createSetsTheDueDateFromTheReasonAndTheClock() {
        BankTransaction fuel = transaction("1250.00", TransactionStatus.SETTLED, TransactionDirection.DEBIT, 3);
        when(transactions.findOwnedByIdForUpdate(TRANSACTION_ID, CUSTOMER)).thenReturn(Optional.of(fuel));

        Dispute dispute = service.create(CUSTOMER, command("250.00")).dispute();

        // The command's reason is P02, a 15-day target.
        assertThat(dispute.getCreatedAt()).isEqualTo(NOW);
        assertThat(dispute.getDueAt()).isEqualTo(NOW.plus(Duration.ofDays(15)));
        assertThat(dispute.isOverdue(NOW)).isFalse();
    }

    @Test
    void createLocksTheTransactionRowBeforeLookingForAnOpenDispute() {
        BankTransaction fuel = transaction("1250.00", TransactionStatus.SETTLED, TransactionDirection.DEBIT, 3);
        when(transactions.findOwnedByIdForUpdate(TRANSACTION_ID, CUSTOMER)).thenReturn(Optional.of(fuel));

        service.create(CUSTOMER, command("250.00"));

        var order = inOrder(transactions, disputes);
        order.verify(transactions).findOwnedByIdForUpdate(TRANSACTION_ID, CUSTOMER);
        order.verify(disputes).findActiveReference(TRANSACTION_ID);
        verify(transactions, never()).findOwnedById(any(), any());
    }

    @Test
    void createAcceptsADisputeForTheWholeTransactionAmountAndRoundsToTwoDecimals() {
        BankTransaction fuel = transaction("1250.00", TransactionStatus.SETTLED, TransactionDirection.DEBIT, 3);
        when(transactions.findOwnedByIdForUpdate(TRANSACTION_ID, CUSTOMER)).thenReturn(Optional.of(fuel));

        DisputeView view = service.create(CUSTOMER, command("1250.000"));

        assertThat(view.dispute().getDisputedAmount()).isEqualTo(new BigDecimal("1250.00"));
    }

    @Test
    void createForATransactionThatIsNotTheCallersIsNotFoundAndTouchesNothingElse() {
        when(transactions.findOwnedByIdForUpdate(TRANSACTION_ID, CUSTOMER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(CUSTOMER, command("250.00")))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("The requested resource was not found.");
        verifyNoInteractions(disputes);
    }

    @Test
    void createOnATransactionWithAnOpenDisputeIsAConflictNamingIt() {
        BankTransaction fuel = transaction("1250.00", TransactionStatus.SETTLED, TransactionDirection.DEBIT, 3);
        when(transactions.findOwnedByIdForUpdate(TRANSACTION_ID, CUSTOMER)).thenReturn(Optional.of(fuel));
        when(disputes.findActiveReference(TRANSACTION_ID)).thenReturn(Optional.of("DSP-2026-000003"));

        assertThatThrownBy(() -> service.create(CUSTOMER, command("250.00")))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.DISPUTE_ALREADY_EXISTS);
                    assertThat(e.getMessage())
                            .isEqualTo("This transaction already has an open dispute (DSP-2026-000003).");
                });
        verify(disputes, never()).save(any());
        verify(disputes, never()).nextReferenceNumber();
    }

    @Test
    void anOpenDisputeIsReportedBeforeAnyOtherEligibilityProblem() {
        // Disputed 119 days ago and still open, so the transaction has since passed the 120-day window.
        BankTransaction old = transaction("1250.00", TransactionStatus.SETTLED, TransactionDirection.DEBIT, 125);
        when(transactions.findOwnedByIdForUpdate(TRANSACTION_ID, CUSTOMER)).thenReturn(Optional.of(old));
        when(disputes.findActiveReference(TRANSACTION_ID)).thenReturn(Optional.of("DSP-2026-000003"));

        assertThatThrownBy(() -> service.create(CUSTOMER, command("250.00")))
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DISPUTE_ALREADY_EXISTS));
    }

    @Test
    void createOnAnIneligibleTransactionIsUnprocessableWithTheCustomerMessage() {
        record Case(BankTransaction transaction, String message) {}
        List<Case> cases = List.of(
                new Case(
                        transaction("1250.00", TransactionStatus.SETTLED, TransactionDirection.CREDIT, 3),
                        "Only money that left your account can be disputed."),
                new Case(
                        transaction("1250.00", TransactionStatus.PENDING, TransactionDirection.DEBIT, 0),
                        "This transaction is still pending. You can dispute it once it has cleared."),
                new Case(
                        transaction("1250.00", TransactionStatus.SETTLED, TransactionDirection.DEBIT, 121),
                        "This transaction is older than 120 days and can no longer be disputed."));

        for (Case c : cases) {
            when(transactions.findOwnedByIdForUpdate(TRANSACTION_ID, CUSTOMER))
                    .thenReturn(Optional.of(c.transaction()));

            assertThatThrownBy(() -> service.create(CUSTOMER, command("250.00")))
                    .isInstanceOfSatisfying(ApiException.class, e -> {
                        assertThat(e.getCode()).isEqualTo(ErrorCode.TRANSACTION_NOT_ELIGIBLE);
                        assertThat(e.getMessage()).isEqualTo(c.message());
                    });
        }
        verify(disputes, never()).save(any());
    }

    @Test
    void anAmountAboveTheTransactionAmountIsAValidationProblemOnAmount() {
        BankTransaction fuel = transaction("1250.00", TransactionStatus.SETTLED, TransactionDirection.DEBIT, 3);
        when(transactions.findOwnedByIdForUpdate(TRANSACTION_ID, CUSTOMER)).thenReturn(Optional.of(fuel));

        assertThatThrownBy(() -> service.create(CUSTOMER, command("1250.01")))
                .isInstanceOfSatisfying(
                        ValidationFailedException.class,
                        e -> assertThat(e.getViolations()).singleElement().satisfies(violation -> {
                            assertThat(violation.field()).isEqualTo("amount");
                            assertThat(violation.message())
                                    .isEqualTo("must not be more than the transaction amount (1250.00)");
                        }));
        verify(disputes, never()).nextReferenceNumber();
        verify(disputes, never()).save(any());
    }

    @Test
    void eligibilityIsCheckedBeforeTheAmount() {
        BankTransaction pending = transaction("1250.00", TransactionStatus.PENDING, TransactionDirection.DEBIT, 0);
        when(transactions.findOwnedByIdForUpdate(TRANSACTION_ID, CUSTOMER)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.create(CUSTOMER, command("999999.00")))
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.TRANSACTION_NOT_ELIGIBLE));
    }

    @Test
    void aFailedCreateNeverConsumesAReferenceNumber() {
        BankTransaction pending = transaction("1250.00", TransactionStatus.PENDING, TransactionDirection.DEBIT, 0);
        when(transactions.findOwnedByIdForUpdate(TRANSACTION_ID, CUSTOMER)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.create(CUSTOMER, command("250.00"))).isInstanceOf(ApiException.class);

        verify(disputes, never()).nextReferenceNumber();
        verify(disputes, never()).save(any());
    }

    // ---- list ----

    @Test
    void listFiltersByCustomerAndPagesNewestFirstWithTheIdAsTieBreaker() {
        when(disputes.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        service.list(CUSTOMER, new DisputeQuery(Set.of(DisputeStatus.SUBMITTED), "fuel", 2, 50));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(disputes).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
    }

    @Test
    void listReturnsTheRepositoryPage() {
        Dispute dispute = dispute(DisputeStatus.SUBMITTED);
        Page<Dispute> page = new PageImpl<>(List.of(dispute));
        when(disputes.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        assertThat(service.list(CUSTOMER, new DisputeQuery(Set.of(), null, 0, 20)))
                .isSameAs(page);
    }

    // ---- get ----

    @Test
    void getReturnsTheDisputeWithItsOwnStatusOnTheTransaction() {
        Dispute dispute = dispute(DisputeStatus.UNDER_REVIEW);
        when(disputes.findDetailById(DISPUTE_ID, CUSTOMER)).thenReturn(Optional.of(dispute));

        DisputeView view = service.get(CUSTOMER, DISPUTE_ID);

        assertThat(view.dispute()).isSameAs(dispute);
        assertThat(view.transactionDisputeStatus()).isEqualTo(DisputeStatus.UNDER_REVIEW);
        verify(disputes, never()).findActiveStatus(any());
    }

    @Test
    void getForADisputeThatIsNotTheCallersIsNotFound() {
        when(disputes.findDetailById(DISPUTE_ID, CUSTOMER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(CUSTOMER, DISPUTE_ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void aWithdrawnDisputeShowsTheStatusOfANewerDisputeOnTheSameTransaction() {
        Dispute withdrawn = dispute(DisputeStatus.WITHDRAWN);
        when(disputes.findDetailById(DISPUTE_ID, CUSTOMER)).thenReturn(Optional.of(withdrawn));
        when(disputes.findActiveStatus(TRANSACTION_ID)).thenReturn(Optional.of(DisputeStatus.UNDER_REVIEW));

        assertThat(service.get(CUSTOMER, DISPUTE_ID).transactionDisputeStatus()).isEqualTo(DisputeStatus.UNDER_REVIEW);
    }

    @Test
    void aWithdrawnDisputeWithNoNewerOneShowsNoStatusOnTheTransaction() {
        Dispute withdrawn = dispute(DisputeStatus.WITHDRAWN);
        when(disputes.findDetailById(DISPUTE_ID, CUSTOMER)).thenReturn(Optional.of(withdrawn));

        assertThat(service.get(CUSTOMER, DISPUTE_ID).transactionDisputeStatus()).isNull();
    }

    // ---- withdraw ----

    @Test
    void withdrawMovesAnOpenDisputeToWithdrawnAndAddsAnEvent() {
        Dispute dispute = dispute(DisputeStatus.SUBMITTED);
        when(disputes.findOwnedByIdForUpdate(DISPUTE_ID, CUSTOMER)).thenReturn(Optional.of(dispute));
        when(disputes.findDetailById(DISPUTE_ID, CUSTOMER)).thenReturn(Optional.of(dispute));

        DisputeView view = service.withdraw(CUSTOMER, DISPUTE_ID);

        assertThat(view.dispute().getStatus()).isEqualTo(DisputeStatus.WITHDRAWN);
        assertThat(view.transactionDisputeStatus()).isNull();
        assertThat(dispute.getResolvedAt()).isEqualTo(NOW);
        assertThat(dispute.getEvents()).hasSize(2);
        DisputeEvent last = dispute.getEvents().getLast();
        assertThat(last.getFromStatus()).isEqualTo(DisputeStatus.SUBMITTED);
        assertThat(last.getToStatus()).isEqualTo(DisputeStatus.WITHDRAWN);
        assertThat(last.getActorId()).isEqualTo(CUSTOMER);
    }

    @Test
    void withdrawLocksTheDisputeBeforeLoadingItsDetail() {
        Dispute dispute = dispute(DisputeStatus.SUBMITTED);
        when(disputes.findOwnedByIdForUpdate(DISPUTE_ID, CUSTOMER)).thenReturn(Optional.of(dispute));
        when(disputes.findDetailById(DISPUTE_ID, CUSTOMER)).thenReturn(Optional.of(dispute));

        service.withdraw(CUSTOMER, DISPUTE_ID);

        var order = inOrder(disputes);
        order.verify(disputes).findOwnedByIdForUpdate(DISPUTE_ID, CUSTOMER);
        order.verify(disputes).findDetailById(DISPUTE_ID, CUSTOMER);
    }

    @Test
    void withdrawingAClosedDisputeIsAnInvalidTransition() {
        Dispute declined = dispute(DisputeStatus.RESOLVED_DECLINED);
        when(disputes.findOwnedByIdForUpdate(DISPUTE_ID, CUSTOMER)).thenReturn(Optional.of(declined));
        when(disputes.findDetailById(DISPUTE_ID, CUSTOMER)).thenReturn(Optional.of(declined));

        assertThatThrownBy(() -> service.withdraw(CUSTOMER, DISPUTE_ID))
                .isInstanceOf(InvalidDisputeTransitionException.class)
                .hasMessage("A dispute in status RESOLVED_DECLINED cannot be withdrawn.");
        assertThat(declined.getStatus()).isEqualTo(DisputeStatus.RESOLVED_DECLINED);
    }

    @Test
    void withdrawingADisputeThatIsNotTheCallersIsNotFoundAndLoadsNothing() {
        when(disputes.findOwnedByIdForUpdate(DISPUTE_ID, CUSTOMER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.withdraw(CUSTOMER, DISPUTE_ID)).isInstanceOf(NotFoundException.class);
        verify(disputes, never()).findDetailById(any(), any());
    }

    // ---- respond ----

    @Test
    void respondAnswersTheInformationRequestAndSendsTheDisputeBackToReview() {
        Dispute dispute = dispute(DisputeStatus.AWAITING_CUSTOMER_INFO);
        when(disputes.findOwnedByIdForUpdate(DISPUTE_ID, CUSTOMER)).thenReturn(Optional.of(dispute));
        when(disputes.findDetailById(DISPUTE_ID, CUSTOMER)).thenReturn(Optional.of(dispute));

        DisputeView view = service.respond(CUSTOMER, DISPUTE_ID, "  Here is the till slip.  ");

        assertThat(view.dispute().getStatus()).isEqualTo(DisputeStatus.UNDER_REVIEW);
        assertThat(view.transactionDisputeStatus()).isEqualTo(DisputeStatus.UNDER_REVIEW);
        DisputeEvent last = dispute.getEvents().getLast();
        assertThat(last.getFromStatus()).isEqualTo(DisputeStatus.AWAITING_CUSTOMER_INFO);
        assertThat(last.getToStatus()).isEqualTo(DisputeStatus.UNDER_REVIEW);
        assertThat(last.getActorId()).isEqualTo(CUSTOMER);
        assertThat(last.getMessage()).isEqualTo("Here is the till slip.");
        assertThat(dispute.getAssignedAgentId())
                .as("the same agent keeps the dispute")
                .isEqualTo(AGENT);
    }

    @Test
    void respondLocksTheDisputeBeforeLoadingItsDetail() {
        Dispute dispute = dispute(DisputeStatus.AWAITING_CUSTOMER_INFO);
        when(disputes.findOwnedByIdForUpdate(DISPUTE_ID, CUSTOMER)).thenReturn(Optional.of(dispute));
        when(disputes.findDetailById(DISPUTE_ID, CUSTOMER)).thenReturn(Optional.of(dispute));

        service.respond(CUSTOMER, DISPUTE_ID, "Here it is.");

        var order = inOrder(disputes);
        order.verify(disputes).findOwnedByIdForUpdate(DISPUTE_ID, CUSTOMER);
        order.verify(disputes).findDetailById(DISPUTE_ID, CUSTOMER);
    }

    @Test
    void respondingWhenNobodyAskedIsAnInvalidTransition() {
        Dispute dispute = dispute(DisputeStatus.UNDER_REVIEW);
        when(disputes.findOwnedByIdForUpdate(DISPUTE_ID, CUSTOMER)).thenReturn(Optional.of(dispute));
        when(disputes.findDetailById(DISPUTE_ID, CUSTOMER)).thenReturn(Optional.of(dispute));

        assertThatThrownBy(() -> service.respond(CUSTOMER, DISPUTE_ID, "Unprompted."))
                .isInstanceOf(InvalidDisputeTransitionException.class)
                .hasMessage("A dispute in status UNDER_REVIEW cannot be answered.");
        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.UNDER_REVIEW);
    }

    @Test
    void respondingToADisputeThatIsNotTheCallersIsNotFound() {
        when(disputes.findOwnedByIdForUpdate(DISPUTE_ID, CUSTOMER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.respond(CUSTOMER, DISPUTE_ID, "Hello."))
                .isInstanceOf(NotFoundException.class);
        verify(disputes, never()).findDetailById(any(), any());
    }

    // ---- helpers ----

    private static CreateDisputeCommand command(String amount) {
        return new CreateDisputeCommand(
                TRANSACTION_ID,
                DisputeReason.P02,
                new BigDecimal(amount),
                "The pump charged me too much for the fuel.");
    }

    private static BankTransaction transaction(
            String amount, TransactionStatus status, TransactionDirection direction, int daysOld) {
        BankTransaction transaction = new BankTransaction(
                null,
                "TXN-20261019-000151",
                NOW.minus(Duration.ofDays(daysOld)),
                new BigDecimal(amount),
                "ZAR",
                direction,
                status,
                "Metro Fuel N1",
                "Fuel",
                Channel.CARD);
        ReflectionTestUtils.setField(transaction, "id", TRANSACTION_ID);
        return transaction;
    }

    /** A dispute walked to the wanted status through the real state machine. */
    private Dispute dispute(DisputeStatus target) {
        BankTransaction transaction = transaction("1250.00", TransactionStatus.SETTLED, TransactionDirection.DEBIT, 3);
        Dispute dispute = stateMachine.create(
                new NewDispute(
                        transaction,
                        CUSTOMER,
                        DisputeReason.P02,
                        new BigDecimal("250.00"),
                        "The pump charged me too much for the fuel.",
                        "DSP-2026-000003"),
                Actor.customer(CUSTOMER));
        ReflectionTestUtils.setField(dispute, "id", DISPUTE_ID);
        switch (target) {
            case SUBMITTED -> {}
            case UNDER_REVIEW -> stateMachine.apply(dispute, DisputeAction.CLAIM, Actor.agent(AGENT), null);
            case AWAITING_CUSTOMER_INFO -> {
                stateMachine.apply(dispute, DisputeAction.CLAIM, Actor.agent(AGENT), null);
                stateMachine.apply(
                        dispute, DisputeAction.REQUEST_INFO, Actor.agent(AGENT), "Please send the till slip.");
            }
            case RESOLVED_DECLINED -> {
                stateMachine.apply(dispute, DisputeAction.CLAIM, Actor.agent(AGENT), null);
                stateMachine.apply(
                        dispute,
                        DisputeAction.RESOLVE_DECLINED,
                        Actor.agent(AGENT),
                        "Declined.",
                        "Merchant proved delivery.");
            }
            case WITHDRAWN -> stateMachine.apply(dispute, DisputeAction.WITHDRAW, Actor.customer(CUSTOMER), null);
            default -> throw new IllegalArgumentException(target.name());
        }
        return dispute;
    }
}
