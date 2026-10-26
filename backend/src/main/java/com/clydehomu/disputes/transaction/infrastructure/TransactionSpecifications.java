package com.clydehomu.disputes.transaction.infrastructure;

import com.clydehomu.disputes.account.domain.Account;
import com.clydehomu.disputes.transaction.domain.BankTransaction;
import com.clydehomu.disputes.transaction.domain.LinkedDispute;
import com.clydehomu.disputes.transaction.domain.TransactionSearch;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Builds the transaction list query. The ownership predicate is always part of it. */
public final class TransactionSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private TransactionSpecifications() {}

    /** Transactions of {@code accountId}, but only if that account belongs to {@code customerId}. */
    public static Specification<BankTransaction> search(UUID customerId, UUID accountId, TransactionSearch search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<BankTransaction, Account> account = root.join("account");
            predicates.add(cb.equal(account.get("id"), accountId));
            predicates.add(cb.equal(account.get("ownerId"), customerId));

            if (search.postedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("postedAt"), search.postedFrom()));
            }
            if (search.postedBefore() != null) {
                predicates.add(cb.lessThan(root.get("postedAt"), search.postedBefore()));
            }
            if (search.text() != null) {
                String pattern = "%" + escapeLike(search.text().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("merchantName")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("reference")), pattern, LIKE_ESCAPE)));
            }
            if (search.disputedOnly()) {
                Subquery<Integer> dispute = query.subquery(Integer.class);
                Root<LinkedDispute> linked = dispute.from(LinkedDispute.class);
                dispute.select(cb.literal(1))
                        .where(
                                cb.equal(linked.get("transactionId"), root.get("id")),
                                cb.notEqual(linked.get("status"), LinkedDispute.WITHDRAWN));
                predicates.add(cb.exists(dispute));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** A customer typing "50%" is looking for those characters, not for a wildcard. */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
