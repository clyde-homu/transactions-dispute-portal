package com.clydehomu.disputes.dispute.application;

import com.clydehomu.disputes.dispute.domain.Actor;
import com.clydehomu.disputes.dispute.domain.Dispute;
import com.clydehomu.disputes.dispute.domain.DisputeAction;
import com.clydehomu.disputes.dispute.domain.DisputeReference;
import com.clydehomu.disputes.dispute.domain.DisputeSearch;
import com.clydehomu.disputes.dispute.domain.DisputeStateMachine;
import com.clydehomu.disputes.dispute.domain.DisputeStatus;
import com.clydehomu.disputes.dispute.domain.NewDispute;
import com.clydehomu.disputes.dispute.infrastructure.DisputeRepository;
import com.clydehomu.disputes.dispute.infrastructure.DisputeSpecifications;
import com.clydehomu.disputes.shared.error.ApiException;
import com.clydehomu.disputes.shared.error.ErrorCode;
import com.clydehomu.disputes.shared.error.NotFoundException;
import com.clydehomu.disputes.shared.error.ValidationFailedException;
import com.clydehomu.disputes.shared.web.ApiFormats;
import com.clydehomu.disputes.transaction.domain.BankTransaction;
import com.clydehomu.disputes.transaction.domain.Eligibility;
import com.clydehomu.disputes.transaction.domain.EligibilityPolicy;
import com.clydehomu.disputes.transaction.infrastructure.BankTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The customer's use cases on disputes. Every method takes the signed-in customer's id and passes it into the query,
 * so another customer's dispute or transaction is simply not found.
 */
@Service
public class DisputeService {

    // Newest first; the id breaks ties so a page boundary never repeats or skips a row.
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

    private final DisputeRepository disputes;
    private final BankTransactionRepository transactions;
    private final EligibilityPolicy eligibilityPolicy;
    private final DisputeStateMachine stateMachine;
    private final Clock clock;

    public DisputeService(
            DisputeRepository disputes,
            BankTransactionRepository transactions,
            EligibilityPolicy eligibilityPolicy,
            DisputeStateMachine stateMachine,
            Clock clock) {
        this.disputes = disputes;
        this.transactions = transactions;
        this.eligibilityPolicy = eligibilityPolicy;
        this.stateMachine = stateMachine;
        this.clock = clock;
    }

    /**
     * Checks run in the order the contract promises: the transaction exists and is the caller's (404), it has no open
     * dispute yet (409), the remaining eligibility rules hold (422), and the amount is not above the transaction's
     * (400). The transaction row is locked first, so two simultaneous requests cannot both pass the 409 check.
     */
    @Transactional
    public DisputeView create(UUID customerId, CreateDisputeCommand command) {
        BankTransaction transaction = transactions
                .findOwnedByIdForUpdate(command.transactionId(), customerId)
                .orElseThrow(DisputeService::notFound);

        disputes.findActiveReference(transaction.getId()).ifPresent(reference -> {
            throw new ApiException(
                    ErrorCode.DISPUTE_ALREADY_EXISTS,
                    "This transaction already has an open dispute (" + reference + ").");
        });

        Instant now = clock.instant();
        // The open-dispute rule was answered above with its own status code, so the policy only judges the others.
        Eligibility eligibility = eligibilityPolicy.evaluate(transaction, false, now);
        if (!eligibility.eligible()) {
            throw new ApiException(ErrorCode.TRANSACTION_NOT_ELIGIBLE, eligibility.message());
        }

        BigDecimal amount = command.amount().setScale(2, RoundingMode.HALF_EVEN);
        if (amount.compareTo(transaction.getAmount()) > 0) {
            throw new ValidationFailedException(
                    "amount",
                    "must not be more than the transaction amount (%s)"
                            .formatted(ApiFormats.money(transaction.getAmount())));
        }

        String reference = DisputeReference.format(now, disputes.nextReferenceNumber());
        Dispute dispute = stateMachine.create(
                new NewDispute(transaction, customerId, command.reason(), amount, command.description(), reference),
                Actor.customer(customerId));
        disputes.save(dispute);
        return view(dispute);
    }

    @Transactional(readOnly = true)
    public Page<Dispute> list(UUID customerId, DisputeQuery query) {
        DisputeSearch search = new DisputeSearch(query.statuses(), query.text());
        return disputes.findAll(
                DisputeSpecifications.search(customerId, search),
                PageRequest.of(query.page(), query.size(), NEWEST_FIRST));
    }

    @Transactional(readOnly = true)
    public DisputeView get(UUID customerId, UUID disputeId) {
        Dispute dispute = disputes.findDetailById(disputeId, customerId).orElseThrow(DisputeService::notFound);
        return view(dispute);
    }

    @Transactional
    public DisputeView withdraw(UUID customerId, UUID disputeId) {
        // Lock first, then load the detail: the second of two simultaneous withdrawals waits here and then finds the
        // dispute already withdrawn.
        disputes.findOwnedByIdForUpdate(disputeId, customerId).orElseThrow(DisputeService::notFound);
        Dispute dispute = disputes.findDetailById(disputeId, customerId).orElseThrow(DisputeService::notFound);
        stateMachine.apply(dispute, DisputeAction.WITHDRAW, Actor.customer(customerId), null);
        return view(dispute);
    }

    private DisputeView view(Dispute dispute) {
        if (dispute.getStatus() != DisputeStatus.WITHDRAWN) {
            return new DisputeView(dispute, dispute.getStatus());
        }
        // A withdrawn dispute does not block the transaction, so a newer dispute on it may be the current one.
        DisputeStatus current =
                disputes.findActiveStatus(dispute.getTransaction().getId()).orElse(null);
        return new DisputeView(dispute, current);
    }

    private static NotFoundException notFound() {
        return new NotFoundException("The requested resource was not found.");
    }
}
