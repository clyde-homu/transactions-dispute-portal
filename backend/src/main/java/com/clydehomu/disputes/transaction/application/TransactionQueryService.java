package com.clydehomu.disputes.transaction.application;

import com.clydehomu.disputes.account.application.AccountService;
import com.clydehomu.disputes.shared.config.BankTimeZone;
import com.clydehomu.disputes.shared.error.NotFoundException;
import com.clydehomu.disputes.shared.error.ValidationFailedException;
import com.clydehomu.disputes.transaction.domain.BankTransaction;
import com.clydehomu.disputes.transaction.domain.Eligibility;
import com.clydehomu.disputes.transaction.domain.EligibilityPolicy;
import com.clydehomu.disputes.transaction.domain.LinkedDispute;
import com.clydehomu.disputes.transaction.domain.TransactionSearch;
import com.clydehomu.disputes.transaction.infrastructure.BankTransactionRepository;
import com.clydehomu.disputes.transaction.infrastructure.LinkedDisputeRepository;
import com.clydehomu.disputes.transaction.infrastructure.TransactionSpecifications;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionQueryService {

    // Newest first; the id breaks ties so a page boundary never repeats or skips a row.
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("postedAt"), Sort.Order.asc("id"));

    private final BankTransactionRepository transactions;
    private final LinkedDisputeRepository linkedDisputes;
    private final AccountService accounts;
    private final EligibilityPolicy eligibilityPolicy;
    private final Clock clock;

    public TransactionQueryService(
            BankTransactionRepository transactions,
            LinkedDisputeRepository linkedDisputes,
            AccountService accounts,
            EligibilityPolicy eligibilityPolicy,
            Clock clock) {
        this.transactions = transactions;
        this.linkedDisputes = linkedDisputes;
        this.accounts = accounts;
        this.eligibilityPolicy = eligibilityPolicy;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<TransactionWithDispute> list(UUID customerId, UUID accountId, TransactionQuery query) {
        if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) {
            throw new ValidationFailedException("from", "must not be after 'to'");
        }
        accounts.requireOwnedBy(customerId, accountId);

        Page<BankTransaction> page = transactions.findAll(
                TransactionSpecifications.search(customerId, accountId, toSearch(query)),
                PageRequest.of(query.page(), query.size(), NEWEST_FIRST));

        Map<UUID, LinkedDispute> disputes = currentDisputes(page.getContent());
        return page.map(transaction -> new TransactionWithDispute(transaction, disputes.get(transaction.getId())));
    }

    @Transactional(readOnly = true)
    public TransactionDetailView get(UUID customerId, UUID transactionId) {
        BankTransaction transaction = transactions
                .findOwnedById(transactionId, customerId)
                .orElseThrow(() -> new NotFoundException("The requested resource was not found."));
        LinkedDispute dispute = currentDisputes(List.of(transaction)).get(transaction.getId());
        Eligibility eligibility = eligibilityPolicy.evaluate(transaction, dispute != null, clock.instant());
        return new TransactionDetailView(new TransactionWithDispute(transaction, dispute), eligibility);
    }

    /** The latest non-withdrawn dispute per transaction, found with one query for the whole batch. */
    private Map<UUID, LinkedDispute> currentDisputes(List<BankTransaction> batch) {
        if (batch.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = batch.stream().map(BankTransaction::getId).toList();
        // Newest first, so keeping the first entry per transaction keeps the latest dispute.
        return linkedDisputes.findActiveByTransactionIds(ids).stream()
                .collect(Collectors.toMap(
                        LinkedDispute::getTransactionId, Function.identity(), (latest, older) -> latest));
    }

    private static TransactionSearch toSearch(TransactionQuery query) {
        Instant from = query.from() == null
                ? null
                : query.from().atStartOfDay(BankTimeZone.ZONE).toInstant();
        // "to" is inclusive, so the range ends at the start of the following day.
        Instant before = query.to() == null
                ? null
                : query.to().plusDays(1).atStartOfDay(BankTimeZone.ZONE).toInstant();
        return new TransactionSearch(from, before, query.text(), query.disputedOnly());
    }
}
