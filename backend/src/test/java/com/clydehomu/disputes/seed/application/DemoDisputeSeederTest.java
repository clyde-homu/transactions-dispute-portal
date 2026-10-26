package com.clydehomu.disputes.seed.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clydehomu.disputes.account.domain.Account;
import com.clydehomu.disputes.account.infrastructure.AccountRepository;
import com.clydehomu.disputes.auth.domain.AppUser;
import com.clydehomu.disputes.auth.domain.Role;
import com.clydehomu.disputes.dispute.domain.ActorRole;
import com.clydehomu.disputes.dispute.domain.Dispute;
import com.clydehomu.disputes.dispute.domain.DisputeEvent;
import com.clydehomu.disputes.dispute.domain.DisputeReason;
import com.clydehomu.disputes.dispute.domain.DisputeStatus;
import com.clydehomu.disputes.dispute.infrastructure.DisputeRepository;
import com.clydehomu.disputes.seed.application.DemoCatalogue.AccountSpec;
import com.clydehomu.disputes.seed.application.DemoCatalogue.CustomerSpec;
import com.clydehomu.disputes.transaction.domain.BankTransaction;
import com.clydehomu.disputes.transaction.domain.EligibilityPolicy;
import com.clydehomu.disputes.transaction.domain.TransactionDirection;
import com.clydehomu.disputes.transaction.infrastructure.BankTransactionRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/** The demo disputes are built from the demo ledgers, so the test generates those ledgers the same way the seeder does. */
class DemoDisputeSeederTest {

    private static final Instant NOW = Instant.parse("2026-10-26T07:00:00Z");
    private static final EligibilityPolicy POLICY = new EligibilityPolicy(120);

    private final DisputeRepository disputes = mock(DisputeRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final BankTransactionRepository transactions = mock(BankTransactionRepository.class);
    private final AtomicLong sequence = new AtomicLong();
    private DemoDisputeSeeder seeder;
    private AppUser agent;

    @BeforeEach
    void setUp() {
        seeder = new DemoDisputeSeeder(disputes, accounts, transactions, POLICY);
        agent = new AppUser(DemoCatalogue.AGENT_USERNAME, "hash", DemoCatalogue.AGENT_DISPLAY_NAME, Role.AGENT, NOW);
        ReflectionTestUtils.setField(agent, "id", UUID.randomUUID());
        when(disputes.nextReferenceNumber()).thenAnswer(call -> sequence.incrementAndGet());
    }

    @Test
    void thandiGetsSixDisputesOneInEveryStatus() {
        AppUser thandi = customerWithLedgers(DemoCatalogue.CUSTOMERS.get(0));

        int created =
                seeder.seedFor(thandi, agent, DemoCatalogue.CUSTOMERS.get(0).disputes(), NOW);

        List<Dispute> saved = saved(6);
        assertThat(created).isEqualTo(6);
        assertThat(saved)
                .extracting(Dispute::getStatus)
                .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(DisputeStatus.class));
        assertThat(saved).extracting(Dispute::getCustomerId).containsOnly(thandi.getId());
    }

    @Test
    void pieterGetsOneDisputeUnderReview() {
        AppUser pieter = customerWithLedgers(DemoCatalogue.CUSTOMERS.get(1));

        int created =
                seeder.seedFor(pieter, agent, DemoCatalogue.CUSTOMERS.get(1).disputes(), NOW);

        assertThat(created).isEqualTo(1);
        Dispute dispute = saved(1).getFirst();
        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.UNDER_REVIEW);
        assertThat(dispute.getReason()).isEqualTo(DisputeReason.F02);
        assertThat(dispute.getCustomerId()).isEqualTo(pieter.getId());
    }

    @Test
    void everyDisputeIsOnAnEligibleDebitThatIsNotOneOfTheNewest() {
        AppUser thandi = customerWithLedgers(DemoCatalogue.CUSTOMERS.get(0));
        seeder.seedFor(thandi, agent, DemoCatalogue.CUSTOMERS.get(0).disputes(), NOW);

        List<Dispute> saved = saved(6);

        assertThat(saved).extracting(d -> d.getTransaction().getId()).doesNotHaveDuplicates();
        for (Dispute dispute : saved) {
            BankTransaction transaction = dispute.getTransaction();
            assertThat(transaction.getDirection()).isEqualTo(TransactionDirection.DEBIT);
            assertThat(POLICY.evaluate(transaction, false, NOW).eligible())
                    .as("%s on %s", dispute.getReference(), transaction.getReference())
                    .isTrue();
            assertThat(transaction.getPostedAt())
                    .as("the newest debits stay free for the demo")
                    .isBefore(NOW.minus(Duration.ofDays(7)));
            assertThat(transaction.getPostedAt())
                    .as("raised after the purchase appeared")
                    .isBefore(dispute.getCreatedAt().minus(Duration.ofHours(23)));
            assertThat(dispute.getCurrency()).isEqualTo(transaction.getCurrency());
            assertThat(dispute.getDisputedAmount()).isLessThanOrEqualTo(transaction.getAmount());
        }
    }

    @Test
    void theStreamBoxDuplicateChargeIsLeftUndisputed() {
        AppUser thandi = customerWithLedgers(DemoCatalogue.CUSTOMERS.get(0));
        seeder.seedFor(thandi, agent, DemoCatalogue.CUSTOMERS.get(0).disputes(), NOW);

        assertThat(saved(6))
                .extracting(d -> d.getTransaction().getMerchantName())
                .doesNotContain("StreamBox Subscription");
    }

    @Test
    void timelinesAreOrderedInThePastAndCarryTheRightActors() {
        AppUser thandi = customerWithLedgers(DemoCatalogue.CUSTOMERS.get(0));
        seeder.seedFor(thandi, agent, DemoCatalogue.CUSTOMERS.get(0).disputes(), NOW);

        for (Dispute dispute : saved(6)) {
            List<DisputeEvent> events = dispute.getEvents();
            assertThat(events).extracting(DisputeEvent::getCreatedAt).isSorted().allMatch(at -> at.isBefore(NOW));
            assertThat(events.getFirst().getFromStatus()).isNull();
            assertThat(events.getFirst().getActorRole()).isEqualTo(ActorRole.CUSTOMER);
            assertThat(events.getFirst().getActorId()).isEqualTo(thandi.getId());
            assertThat(dispute.getCreatedAt()).isEqualTo(events.getFirst().getCreatedAt());
            assertThat(dispute.getUpdatedAt()).isEqualTo(events.getLast().getCreatedAt());
            assertThat(dispute.getResolvedAt())
                    .isEqualTo(dispute.getStatus().isClosed() ? dispute.getUpdatedAt() : null);
            for (DisputeEvent event : events) {
                if (event.getActorRole() == ActorRole.AGENT) {
                    assertThat(event.getActorId()).as("the demo agent acts").isEqualTo(agent.getId());
                } else {
                    assertThat(event.getActorId()).isEqualTo(thandi.getId());
                }
            }
        }
    }

    @Test
    void everyClaimedDisputeIsAssignedToTheDemoAgentAndTheSubmittedOneIsNot() {
        AppUser thandi = customerWithLedgers(DemoCatalogue.CUSTOMERS.get(0));
        seeder.seedFor(thandi, agent, DemoCatalogue.CUSTOMERS.get(0).disputes(), NOW);

        for (Dispute dispute : saved(6)) {
            boolean claimed = dispute.getEvents().stream().anyMatch(e -> e.getActorRole() == ActorRole.AGENT);
            assertThat(dispute.getAssignedAgentId())
                    .as("%s (%s)", dispute.getReference(), dispute.getStatus())
                    .isEqualTo(claimed ? agent.getId() : null);
        }
        assertThat(byStatus(saved(6), DisputeStatus.SUBMITTED).getAssignedAgentId())
                .as("the queue is never empty")
                .isNull();
    }

    @Test
    void resolutionsCarryAnInternalNoteAndNoOtherStepDoes() {
        AppUser thandi = customerWithLedgers(DemoCatalogue.CUSTOMERS.get(0));
        seeder.seedFor(thandi, agent, DemoCatalogue.CUSTOMERS.get(0).disputes(), NOW);

        for (Dispute dispute : saved(6)) {
            for (DisputeEvent event : dispute.getEvents()) {
                boolean resolution = event.getToStatus() == DisputeStatus.RESOLVED_UPHELD
                        || event.getToStatus() == DisputeStatus.RESOLVED_DECLINED;
                if (resolution) {
                    assertThat(event.getInternalNote()).isNotBlank();
                } else {
                    assertThat(event.getInternalNote()).isNull();
                }
            }
        }
    }

    @Test
    void backOfficeStepsCarryRealisticMessagesForTheCustomer() {
        AppUser thandi = customerWithLedgers(DemoCatalogue.CUSTOMERS.get(0));
        seeder.seedFor(thandi, agent, DemoCatalogue.CUSTOMERS.get(0).disputes(), NOW);

        Dispute underReview = byStatus(saved(6), DisputeStatus.UNDER_REVIEW);
        Dispute awaiting = byStatus(saved(6), DisputeStatus.AWAITING_CUSTOMER_INFO);
        Dispute upheld = byStatus(saved(6), DisputeStatus.RESOLVED_UPHELD);

        assertThat(underReview.getEvents().getLast().getMessage())
                .isEqualTo("We've asked the merchant for proof of delivery.");
        assertThat(awaiting.getEvents().getLast().getActorRole()).isEqualTo(ActorRole.AGENT);
        assertThat(awaiting.getEvents().getLast().getMessage()).startsWith("Please send us");
        assertThat(upheld.getEvents())
                .extracting(DisputeEvent::getToStatus)
                .containsExactly(
                        DisputeStatus.SUBMITTED,
                        DisputeStatus.UNDER_REVIEW,
                        DisputeStatus.AWAITING_CUSTOMER_INFO,
                        DisputeStatus.UNDER_REVIEW,
                        DisputeStatus.RESOLVED_UPHELD);
        assertThat(upheld.getEvents().get(3).getActorRole()).isEqualTo(ActorRole.CUSTOMER);
        assertThat(upheld.getEvents().get(3).getMessage()).isNotBlank();
    }

    @Test
    void theGymDebitOrderIsTheSubmittedDisputeAndAPartialAmountIsAFractionOfTheTransaction() {
        AppUser thandi = customerWithLedgers(DemoCatalogue.CUSTOMERS.get(0));
        seeder.seedFor(thandi, agent, DemoCatalogue.CUSTOMERS.get(0).disputes(), NOW);
        List<Dispute> saved = saved(6);

        Dispute submitted = byStatus(saved, DisputeStatus.SUBMITTED);
        Dispute partial = byStatus(saved, DisputeStatus.AWAITING_CUSTOMER_INFO);

        assertThat(submitted.getTransaction().getMerchantName()).isEqualTo("Sunrise Gym Membership");
        assertThat(submitted.getEvents()).hasSize(1);
        assertThat(partial.getReason()).isEqualTo(DisputeReason.P02);
        assertThat(partial.getDisputedAmount())
                .isCloseTo(
                        partial.getTransaction().getAmount().multiply(new BigDecimal("0.40")),
                        within(new BigDecimal("0.005")));
    }

    @Test
    void descriptionsNameTheMerchantAndMeetTheContractLength() {
        AppUser thandi = customerWithLedgers(DemoCatalogue.CUSTOMERS.get(0));
        seeder.seedFor(thandi, agent, DemoCatalogue.CUSTOMERS.get(0).disputes(), NOW);

        for (Dispute dispute : saved(6)) {
            assertThat(dispute.getDescription())
                    .contains(dispute.getTransaction().getMerchantName())
                    .hasSizeBetween(20, 1000);
        }
    }

    @Test
    void referencesComeFromTheSequenceAndUseTheYearTheDisputeWasRaised() {
        AppUser thandi = customerWithLedgers(DemoCatalogue.CUSTOMERS.get(0));
        seeder.seedFor(thandi, agent, DemoCatalogue.CUSTOMERS.get(0).disputes(), NOW);

        assertThat(saved(6))
                .extracting(Dispute::getReference)
                .containsExactly(
                        "DSP-2026-000001",
                        "DSP-2026-000002",
                        "DSP-2026-000003",
                        "DSP-2026-000004",
                        "DSP-2026-000005",
                        "DSP-2026-000006");
    }

    @Test
    void aCustomerWhoAlreadyHasDisputesGetsNoMore() {
        AppUser thandi = customerWithLedgers(DemoCatalogue.CUSTOMERS.get(0));
        when(disputes.existsByCustomerId(thandi.getId())).thenReturn(true);

        int created =
                seeder.seedFor(thandi, agent, DemoCatalogue.CUSTOMERS.get(0).disputes(), NOW);

        assertThat(created).isZero();
        verify(disputes, never()).save(any());
        verify(disputes, never()).nextReferenceNumber();
    }

    @Test
    void aLedgerWithNothingEligibleSkipsTheDisputeInsteadOfFailingStartup() {
        AppUser thandi = customerWithLedgers(DemoCatalogue.CUSTOMERS.get(0));
        when(transactions.findByAccountId(any())).thenReturn(List.of());

        int created =
                seeder.seedFor(thandi, agent, DemoCatalogue.CUSTOMERS.get(0).disputes(), NOW);

        assertThat(created).isZero();
        verify(disputes, never()).save(any());
    }

    // ---- helpers ----

    private List<Dispute> saved(int expected) {
        ArgumentCaptor<Dispute> captor = ArgumentCaptor.forClass(Dispute.class);
        verify(disputes, times(expected)).save(captor.capture());
        return captor.getAllValues();
    }

    private static Dispute byStatus(List<Dispute> disputes, DisputeStatus status) {
        return disputes.stream()
                .filter(d -> d.getStatus() == status)
                .findFirst()
                .orElseThrow();
    }

    /** Generates each account's ledger like the seeder does and wires the mocks so the seeder finds them. */
    private AppUser customerWithLedgers(CustomerSpec spec) {
        AppUser user = new AppUser(spec.username(), "hash", spec.displayName(), Role.CUSTOMER, NOW);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        List<Account> stored = new ArrayList<>();
        int reference = 0;
        for (AccountSpec accountSpec : spec.accounts()) {
            Account account = new Account(
                    user.getId(),
                    accountSpec.maskedNumber(),
                    accountSpec.productName(),
                    DemoCatalogue.CURRENCY,
                    accountSpec.balance(),
                    NOW);
            ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
            stored.add(account);
            List<BankTransaction> ledger = new ArrayList<>();
            for (DemoLedgerGenerator.Draft draft :
                    new DemoLedgerGenerator().generate(accountSpec.ledger(), NOW, new Random(20_261_026L))) {
                BankTransaction transaction = new BankTransaction(
                        account,
                        "TXN-TEST-%d-%d".formatted(stored.size(), ++reference),
                        draft.postedAt(),
                        draft.amount(),
                        DemoCatalogue.CURRENCY,
                        draft.direction(),
                        draft.status(),
                        draft.merchantName(),
                        draft.merchantCategory(),
                        draft.channel());
                ReflectionTestUtils.setField(transaction, "id", UUID.randomUUID());
                ledger.add(transaction);
            }
            when(transactions.findByAccountId(account.getId())).thenReturn(ledger);
        }
        when(accounts.findByOwnerIdOrderByProductNameAsc(user.getId())).thenReturn(stored);
        return user;
    }
}
