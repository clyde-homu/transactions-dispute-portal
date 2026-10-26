package com.clydehomu.disputes.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clydehomu.disputes.agent.domain.AgentAction;
import com.clydehomu.disputes.agent.domain.AgentQueue;
import com.clydehomu.disputes.agent.domain.ResolutionOutcome;
import com.clydehomu.disputes.auth.domain.AppUser;
import com.clydehomu.disputes.auth.domain.Role;
import com.clydehomu.disputes.auth.infrastructure.AppUserRepository;
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
import com.clydehomu.disputes.shared.error.NotFoundException;
import com.clydehomu.disputes.transaction.domain.BankTransaction;
import com.clydehomu.disputes.transaction.domain.Channel;
import com.clydehomu.disputes.transaction.domain.TransactionDirection;
import com.clydehomu.disputes.transaction.domain.TransactionStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

class AgentDisputeServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-26T07:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID CUSTOMER = UUID.fromString("6d0b8a51-3f2c-4e7a-9b14-0c5e2f7a9d31");
    private static final UUID LERATO = UUID.fromString("3b9a7c52-1d4e-4f68-a0b3-7c8d9e0f1a2b");
    private static final UUID SIPHO = UUID.fromString("9c8b7a65-4e3d-4c2b-8a19-0f1e2d3c4b5a");
    private static final UUID TRANSACTION_ID = UUID.fromString("5e8a1c3d-7b2f-4a9e-8c6d-0f1e2a3b4c5d");
    private static final UUID DISPUTE_ID = UUID.fromString("0f9e8d7c-6b5a-4c3d-8e2f-1a0b9c8d7e6f");

    private final DisputeRepository disputes = mock(DisputeRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final DisputeStateMachine stateMachine = new DisputeStateMachine(CLOCK);
    private AgentDisputeService service;

    @BeforeEach
    void setUp() {
        service = new AgentDisputeService(disputes, users, stateMachine);
        when(users.findAllById(anyCollection()))
                .thenReturn(List.of(
                        user(CUSTOMER, "thandi", "Thandi Mokoena", Role.CUSTOMER),
                        user(LERATO, "lerato", "Lerato Dlamini", Role.AGENT),
                        user(SIPHO, "sipho", "Sipho Nkosi", Role.AGENT)));
        when(disputes.findActiveStatus(any())).thenReturn(Optional.empty());
    }

    // ---- get and available actions ----

    @Test
    void getReturnsTheDisputeWithNamesAndTheCallersActions() {
        Dispute dispute = dispute(DisputeStatus.UNDER_REVIEW);
        when(disputes.findAnyDetailById(DISPUTE_ID)).thenReturn(Optional.of(dispute));

        AgentDisputeView view = service.get(LERATO, DISPUTE_ID);

        assertThat(view.dispute()).isSameAs(dispute);
        assertThat(view.customerName()).isEqualTo("Thandi Mokoena");
        assertThat(view.assignedAgentName()).isEqualTo("Lerato Dlamini");
        assertThat(view.transactionDisputeStatus()).isEqualTo(DisputeStatus.UNDER_REVIEW);
        assertThat(dispute.getEvents()).extracting(view::actorName).containsExactly("Thandi Mokoena", "Lerato Dlamini");
    }

    @Test
    void anUnknownDisputeIsNotFound() {
        when(disputes.findAnyDetailById(DISPUTE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(LERATO, DISPUTE_ID)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.claim(LERATO, DISPUTE_ID)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.requestInfo(LERATO, DISPUTE_ID, "Question?"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.resolve(LERATO, DISPUTE_ID, ResolutionOutcome.UPHELD, "Done.", "Note."))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void aSubmittedUnassignedDisputeOffersEveryAgentTheClaim() {
        when(disputes.findAnyDetailById(DISPUTE_ID)).thenReturn(Optional.of(dispute(DisputeStatus.SUBMITTED)));

        assertThat(service.get(LERATO, DISPUTE_ID).availableActions()).containsExactly(AgentAction.CLAIM);
        assertThat(service.get(SIPHO, DISPUTE_ID).availableActions()).containsExactly(AgentAction.CLAIM);
    }

    @Test
    void anUnderReviewDisputeOffersAskAndResolveToItsAgentOnly() {
        when(disputes.findAnyDetailById(DISPUTE_ID)).thenReturn(Optional.of(dispute(DisputeStatus.UNDER_REVIEW)));

        assertThat(service.get(LERATO, DISPUTE_ID).availableActions())
                .containsExactly(AgentAction.REQUEST_INFO, AgentAction.RESOLVE);
        assertThat(service.get(SIPHO, DISPUTE_ID).availableActions()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(
            value = DisputeStatus.class,
            names = {"AWAITING_CUSTOMER_INFO", "RESOLVED_UPHELD", "RESOLVED_DECLINED", "WITHDRAWN"})
    void waitingAndClosedDisputesOfferNoActionsToAnyone(DisputeStatus status) {
        when(disputes.findAnyDetailById(DISPUTE_ID)).thenReturn(Optional.of(dispute(status)));

        assertThat(service.get(LERATO, DISPUTE_ID).availableActions()).isEmpty();
        assertThat(service.get(SIPHO, DISPUTE_ID).availableActions()).isEmpty();
    }

    @Test
    void aWithdrawnDisputeShowsTheStatusOfANewerDisputeOnTheSameTransaction() {
        when(disputes.findAnyDetailById(DISPUTE_ID)).thenReturn(Optional.of(dispute(DisputeStatus.WITHDRAWN)));
        when(disputes.findActiveStatus(TRANSACTION_ID)).thenReturn(Optional.of(DisputeStatus.SUBMITTED));

        assertThat(service.get(LERATO, DISPUTE_ID).transactionDisputeStatus()).isEqualTo(DisputeStatus.SUBMITTED);
    }

    @Test
    void anEventWhoseActorIsNotKnownHasNoActorName() {
        Dispute dispute = dispute(DisputeStatus.UNDER_REVIEW);
        when(disputes.findAnyDetailById(DISPUTE_ID)).thenReturn(Optional.of(dispute));
        when(users.findAllById(anyCollection()))
                .thenReturn(List.of(user(CUSTOMER, "thandi", "Thandi Mokoena", Role.CUSTOMER)));

        AgentDisputeView view = service.get(LERATO, DISPUTE_ID);

        assertThat(view.actorName(dispute.getEvents().getLast())).isNull();
        assertThat(view.assignedAgentName()).isNull();
    }

    @Test
    void anUnassignedDisputeHasNoAssignedAgentName() {
        when(disputes.findAnyDetailById(DISPUTE_ID)).thenReturn(Optional.of(dispute(DisputeStatus.SUBMITTED)));

        assertThat(service.get(LERATO, DISPUTE_ID).assignedAgentName()).isNull();
    }

    // ---- claim ----

    @Test
    void claimAssignsTheDisputeToTheCallerAndMovesItUnderReview() {
        Dispute dispute = dispute(DisputeStatus.SUBMITTED);
        when(disputes.findAnyDetailById(DISPUTE_ID)).thenReturn(Optional.of(dispute));

        AgentDisputeView view = service.claim(LERATO, DISPUTE_ID);

        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.UNDER_REVIEW);
        assertThat(dispute.getAssignedAgentId()).isEqualTo(LERATO);
        assertThat(view.assignedAgentName()).isEqualTo("Lerato Dlamini");
        assertThat(view.availableActions()).containsExactly(AgentAction.REQUEST_INFO, AgentAction.RESOLVE);
        DisputeEvent claim = dispute.getEvents().getLast();
        assertThat(claim.getActorId()).isEqualTo(LERATO);
        assertThat(view.actorName(claim)).isEqualTo("Lerato Dlamini");
    }

    @Test
    void aSecondAgentCannotClaimADisputeThatIsAlreadyClaimed() {
        Dispute dispute = dispute(DisputeStatus.UNDER_REVIEW);
        when(disputes.findAnyDetailById(DISPUTE_ID)).thenReturn(Optional.of(dispute));

        assertThatThrownBy(() -> service.claim(SIPHO, DISPUTE_ID))
                .isInstanceOf(InvalidDisputeTransitionException.class)
                .hasMessage("A dispute in status UNDER_REVIEW cannot be claimed.");
        assertThat(dispute.getAssignedAgentId()).isEqualTo(LERATO);
    }

    // ---- request info ----

    @Test
    void requestInfoByTheAssignedAgentWaitsForTheCustomerAndRecordsTheQuestion() {
        Dispute dispute = dispute(DisputeStatus.UNDER_REVIEW);
        when(disputes.findAnyDetailById(DISPUTE_ID)).thenReturn(Optional.of(dispute));

        AgentDisputeView view = service.requestInfo(LERATO, DISPUTE_ID, "Please send the till slip.");

        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.AWAITING_CUSTOMER_INFO);
        assertThat(view.transactionDisputeStatus()).isEqualTo(DisputeStatus.AWAITING_CUSTOMER_INFO);
        assertThat(view.availableActions()).isEmpty();
        DisputeEvent last = dispute.getEvents().getLast();
        assertThat(last.getMessage()).isEqualTo("Please send the till slip.");
        assertThat(last.getInternalNote()).isNull();
        assertThat(last.getActorId()).isEqualTo(LERATO);
    }

    @Test
    void requestInfoByAnotherAgentIsAnInvalidTransitionAndChangesNothing() {
        Dispute dispute = dispute(DisputeStatus.UNDER_REVIEW);
        when(disputes.findAnyDetailById(DISPUTE_ID)).thenReturn(Optional.of(dispute));

        assertThatThrownBy(() -> service.requestInfo(SIPHO, DISPUTE_ID, "Question?"))
                .isInstanceOf(InvalidDisputeTransitionException.class)
                .hasMessage("Only the assigned agent can request information on this dispute.");
        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.UNDER_REVIEW);
        assertThat(dispute.getEvents()).hasSize(2);
    }

    @Test
    void requestInfoOnADisputeNobodyHasClaimedIsAnInvalidTransition() {
        when(disputes.findAnyDetailById(DISPUTE_ID)).thenReturn(Optional.of(dispute(DisputeStatus.SUBMITTED)));

        assertThatThrownBy(() -> service.requestInfo(LERATO, DISPUTE_ID, "Question?"))
                .isInstanceOf(InvalidDisputeTransitionException.class);
    }

    // ---- resolve ----

    @Test
    void resolveUpheldClosesTheDisputeAndKeepsTheInternalNoteOnTheEvent() {
        Dispute dispute = dispute(DisputeStatus.UNDER_REVIEW);
        when(disputes.findAnyDetailById(DISPUTE_ID)).thenReturn(Optional.of(dispute));

        AgentDisputeView view = service.resolve(
                LERATO, DISPUTE_ID, ResolutionOutcome.UPHELD, "We reversed the charge.", "Acquirer confirmed.");

        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.RESOLVED_UPHELD);
        assertThat(dispute.getResolvedAt()).isEqualTo(NOW);
        assertThat(dispute.getAssignedAgentId())
                .as("assignment survives resolution")
                .isEqualTo(LERATO);
        assertThat(view.availableActions()).isEmpty();
        DisputeEvent last = dispute.getEvents().getLast();
        assertThat(last.getMessage()).isEqualTo("We reversed the charge.");
        assertThat(last.getInternalNote()).isEqualTo("Acquirer confirmed.");
    }

    @Test
    void resolveDeclinedClosesTheDisputeAsDeclined() {
        Dispute dispute = dispute(DisputeStatus.UNDER_REVIEW);
        when(disputes.findAnyDetailById(DISPUTE_ID)).thenReturn(Optional.of(dispute));

        service.resolve(LERATO, DISPUTE_ID, ResolutionOutcome.DECLINED, "We could not uphold it.", "Delivery proven.");

        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.RESOLVED_DECLINED);
    }

    @Test
    void resolveByAnotherAgentOrInTheWrongStatusIsAnInvalidTransition() {
        Dispute underReview = dispute(DisputeStatus.UNDER_REVIEW);
        when(disputes.findAnyDetailById(DISPUTE_ID)).thenReturn(Optional.of(underReview));
        assertThatThrownBy(() -> service.resolve(SIPHO, DISPUTE_ID, ResolutionOutcome.UPHELD, "Done.", "Note."))
                .isInstanceOf(InvalidDisputeTransitionException.class);
        assertThat(underReview.getStatus()).isEqualTo(DisputeStatus.UNDER_REVIEW);

        Dispute awaiting = dispute(DisputeStatus.AWAITING_CUSTOMER_INFO);
        when(disputes.findAnyDetailById(DISPUTE_ID)).thenReturn(Optional.of(awaiting));
        assertThatThrownBy(() -> service.resolve(LERATO, DISPUTE_ID, ResolutionOutcome.DECLINED, "Done.", "Note."))
                .isInstanceOf(InvalidDisputeTransitionException.class);
    }

    // ---- list ----

    @Test
    void listReturnsOldestFirstWithNamesFromASingleLookup() {
        Dispute unassigned = dispute(DisputeStatus.SUBMITTED);
        Dispute claimed = dispute(DisputeStatus.UNDER_REVIEW);
        when(disputes.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(call -> new PageImpl<>(List.of(unassigned, claimed), call.getArgument(1), 41));

        Page<AgentDisputeSummaryView> page =
                service.list(LERATO, new AgentDisputeQuery(AgentQueue.ALL, Set.of(), null, 1, 2));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(disputes).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(2);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")));
        assertThat(page.getTotalElements()).isEqualTo(41);
        assertThat(page.getContent())
                .extracting(AgentDisputeSummaryView::customerName, AgentDisputeSummaryView::assignedAgentName)
                .containsExactly(tuple("Thandi Mokoena", null), tuple("Thandi Mokoena", "Lerato Dlamini"));
        verify(users, times(1)).findAllById(anyCollection());
    }

    @Test
    void listOfAnEmptyPageDoesNotFailAndDoesNotNeedNames() {
        when(disputes.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(call -> new PageImpl<Dispute>(List.of(), call.getArgument(1), 0));
        when(users.findAllById(anyCollection())).thenReturn(List.of());

        Page<AgentDisputeSummaryView> page =
                service.list(LERATO, new AgentDisputeQuery(AgentQueue.MINE, Set.of(), "none", 0, 20));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    // ---- helpers ----

    private static AppUser user(UUID id, String username, String displayName, Role role) {
        AppUser user = new AppUser(username, "hash", displayName, role, NOW);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    /** A dispute walked to the wanted status through the real state machine; agent steps are Lerato's. */
    private Dispute dispute(DisputeStatus target) {
        BankTransaction transaction = new BankTransaction(
                null,
                "TXN-20261019-000151",
                NOW.minusSeconds(3 * 86_400L),
                new BigDecimal("1250.00"),
                "ZAR",
                TransactionDirection.DEBIT,
                TransactionStatus.SETTLED,
                "Metro Fuel N1",
                "Fuel",
                Channel.CARD);
        ReflectionTestUtils.setField(transaction, "id", TRANSACTION_ID);
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
        Actor lerato = Actor.agent(LERATO);
        switch (target) {
            case SUBMITTED -> {}
            case UNDER_REVIEW -> stateMachine.apply(dispute, DisputeAction.CLAIM, lerato, null);
            case AWAITING_CUSTOMER_INFO -> {
                stateMachine.apply(dispute, DisputeAction.CLAIM, lerato, null);
                stateMachine.apply(dispute, DisputeAction.REQUEST_INFO, lerato, "Please send the till slip.");
            }
            case RESOLVED_UPHELD -> {
                stateMachine.apply(dispute, DisputeAction.CLAIM, lerato, null);
                stateMachine.apply(dispute, DisputeAction.RESOLVE_UPHELD, lerato, "Upheld.", "Checked.");
            }
            case RESOLVED_DECLINED -> {
                stateMachine.apply(dispute, DisputeAction.CLAIM, lerato, null);
                stateMachine.apply(dispute, DisputeAction.RESOLVE_DECLINED, lerato, "Declined.", "Checked.");
            }
            case WITHDRAWN -> stateMachine.apply(dispute, DisputeAction.WITHDRAW, Actor.customer(CUSTOMER), null);
        }
        return dispute;
    }
}
