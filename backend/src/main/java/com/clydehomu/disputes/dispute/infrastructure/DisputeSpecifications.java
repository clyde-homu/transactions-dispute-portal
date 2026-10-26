package com.clydehomu.disputes.dispute.infrastructure;

import com.clydehomu.disputes.dispute.domain.Dispute;
import com.clydehomu.disputes.dispute.domain.DisputeSearch;
import com.clydehomu.disputes.transaction.domain.BankTransaction;
import com.clydehomu.disputes.transaction.infrastructure.TransactionSpecifications;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Builds the dispute history query. The ownership predicate is always part of it. */
public final class DisputeSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private DisputeSpecifications() {}

    @SuppressWarnings("unchecked")
    public static Specification<Dispute> search(UUID customerId, DisputeSearch search) {
        return (root, query, cb) -> {
            // The merchant name is both filtered on and shown in every row, so the transaction is fetched in the same
            // query. The count query selects only a number, where a fetch is meaningless and not allowed. Hibernate's
            // fetch is also a join, which lets the filter reuse it instead of joining the table twice.
            boolean countQuery = Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType());
            Join<Dispute, BankTransaction> transaction = countQuery
                    ? root.join("transaction")
                    : (Join<Dispute, BankTransaction>) (Fetch<?, ?>) root.fetch("transaction");

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("customerId"), customerId));
            if (search.statuses() != null && !search.statuses().isEmpty()) {
                predicates.add(root.get("status").in(search.statuses()));
            }
            if (search.text() != null) {
                String pattern =
                        "%" + TransactionSpecifications.escapeLike(search.text().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("reference")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(transaction.get("merchantName")), pattern, LIKE_ESCAPE)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
