package com.clydehomu.disputes.seed.application;

import com.clydehomu.disputes.account.domain.Account;
import com.clydehomu.disputes.account.infrastructure.AccountRepository;
import com.clydehomu.disputes.auth.domain.AppUser;
import com.clydehomu.disputes.dispute.domain.Actor;
import com.clydehomu.disputes.dispute.domain.ActorRole;
import com.clydehomu.disputes.dispute.domain.Dispute;
import com.clydehomu.disputes.dispute.domain.DisputeReference;
import com.clydehomu.disputes.dispute.domain.DisputeStateMachine;
import com.clydehomu.disputes.dispute.domain.NewDispute;
import com.clydehomu.disputes.dispute.infrastructure.DisputeRepository;
import com.clydehomu.disputes.seed.application.DemoCatalogue.DisputeSpec;
import com.clydehomu.disputes.seed.application.DemoCatalogue.DisputeStep;
import com.clydehomu.disputes.transaction.domain.BankTransaction;
import com.clydehomu.disputes.transaction.domain.EligibilityPolicy;
import com.clydehomu.disputes.transaction.infrastructure.BankTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds the demo disputes by driving each one through {@link DisputeStateMachine}, so the seeded timelines carry the
 * same statuses, timestamps and audit rows that real use would. Back-office steps are recorded as the demo agent, who
 * also becomes the assignee of every dispute she claims.
 *
 * <p>Transactions are chosen from the customer's own ledger, never named, so the data stays valid whatever day it is
 * loaded: only debits the eligibility policy accepts, at least a day older than the dispute, and never one of the newest
 * ones. Those stay free so the demo (and the end-to-end test) can raise a fresh dispute on the StreamBox duplicate
 * charge and a few other recent purchases.
 */
final class DemoDisputeSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDisputeSeeder.class);

    /** A customer disputes a purchase after it has appeared on their statement, not in the same minute. */
    private static final Duration POSTED_BEFORE_RAISED = Duration.ofDays(1);

    /**
     * Debits newer than this are never seeded as disputed. The duplicate charge is posted five days before the demo
     * starts, so a week keeps it and the purchases around it clear.
     */
    private static final Duration KEEP_RECENT_UNDISPUTED = Duration.ofDays(7);

    private final DisputeRepository disputes;
    private final AccountRepository accounts;
    private final BankTransactionRepository transactions;
    private final EligibilityPolicy eligibilityPolicy;

    DemoDisputeSeeder(
            DisputeRepository disputes,
            AccountRepository accounts,
            BankTransactionRepository transactions,
            EligibilityPolicy eligibilityPolicy) {
        this.disputes = disputes;
        this.accounts = accounts;
        this.transactions = transactions;
        this.eligibilityPolicy = eligibilityPolicy;
    }

    /**
     * Seeds the customer's disputes unless they already have any, so running it again adds nothing.
     *
     * @param agent the back-office user who performs the claim, information request and resolution steps
     * @return how many disputes were created
     */
    int seedFor(AppUser customer, AppUser agent, List<DisputeSpec> specs, Instant now) {
        if (specs.isEmpty() || disputes.existsByCustomerId(customer.getId())) {
            return 0;
        }
        Map<String, List<BankTransaction>> ledgers = ledgersByProduct(customer.getId());
        Set<UUID> alreadyUsed = new HashSet<>();
        int created = 0;
        for (DisputeSpec spec : specs) {
            Instant raisedAt = now.minus(spec.raisedAgo());
            Optional<BankTransaction> transaction =
                    pick(ledgers.getOrDefault(spec.accountProduct(), List.of()), spec, raisedAt, now, alreadyUsed);
            if (transaction.isEmpty()) {
                // A short ledger should not stop the application from starting; the demo just has fewer disputes.
                log.warn("No eligible {} transaction for a demo dispute of {}", spec.accountProduct(), spec.reason());
                continue;
            }
            alreadyUsed.add(transaction.get().getId());
            disputes.save(build(customer.getId(), agent.getId(), spec, transaction.get(), raisedAt));
            created++;
        }
        return created;
    }

    private Map<String, List<BankTransaction>> ledgersByProduct(UUID customerId) {
        Map<String, List<BankTransaction>> ledgers = new HashMap<>();
        for (Account account : accounts.findByOwnerIdOrderByProductNameAsc(customerId)) {
            ledgers.computeIfAbsent(account.getProductName(), product -> new ArrayList<>())
                    .addAll(transactions.findByAccountId(account.getId()));
        }
        return ledgers;
    }

    /** The newest transaction that fits: eligible, old enough, on the right merchant, and not used by another dispute. */
    private Optional<BankTransaction> pick(
            List<BankTransaction> ledger, DisputeSpec spec, Instant raisedAt, Instant now, Set<UUID> alreadyUsed) {
        Instant latestPosting = earlier(raisedAt.minus(POSTED_BEFORE_RAISED), now.minus(KEEP_RECENT_UNDISPUTED));
        return ledger.stream()
                .filter(tx -> !alreadyUsed.contains(tx.getId()))
                .filter(tx -> spec.merchantName() == null || spec.merchantName().equals(tx.getMerchantName()))
                .filter(tx -> !tx.getPostedAt().isAfter(latestPosting))
                .filter(tx -> eligibilityPolicy.evaluate(tx, false, now).eligible())
                .max(Comparator.comparing(BankTransaction::getPostedAt));
    }

    private Dispute build(
            UUID customerId, UUID agentId, DisputeSpec spec, BankTransaction transaction, Instant raisedAt) {
        BigDecimal amount = transaction
                .getAmount()
                .multiply(BigDecimal.valueOf(spec.percentOfAmount()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_EVEN);
        String reference = DisputeReference.format(raisedAt, disputes.nextReferenceNumber());
        NewDispute request = new NewDispute(
                transaction,
                customerId,
                spec.reason(),
                amount,
                spec.description().formatted(transaction.getMerchantName()),
                reference);

        // A machine per step, each reading its own fixed clock, so every event gets the moment it should have had.
        Instant at = raisedAt;
        Dispute dispute = machineAt(at).create(request, Actor.customer(customerId));
        for (DisputeStep step : spec.steps()) {
            at = at.plus(step.after());
            Actor actor =
                    step.action().actor() == ActorRole.CUSTOMER ? Actor.customer(customerId) : Actor.agent(agentId);
            machineAt(at).apply(dispute, step.action(), actor, step.message(), step.internalNote());
        }
        return dispute;
    }

    private static DisputeStateMachine machineAt(Instant at) {
        return new DisputeStateMachine(Clock.fixed(at, ZoneOffset.UTC));
    }

    private static Instant earlier(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
