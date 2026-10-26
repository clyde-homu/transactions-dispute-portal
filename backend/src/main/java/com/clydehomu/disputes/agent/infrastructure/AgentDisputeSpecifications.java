package com.clydehomu.disputes.agent.infrastructure;

import com.clydehomu.disputes.agent.domain.AgentDisputeSearch;
import com.clydehomu.disputes.auth.domain.AppUser;
import com.clydehomu.disputes.dispute.domain.Dispute;
import com.clydehomu.disputes.transaction.domain.BankTransaction;
import com.clydehomu.disputes.transaction.infrastructure.TransactionSpecifications;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * Builds the agent work-queue query. Unlike the customer history it has no ownership predicate: agents reach every
 * customer's disputes, which is why only the agent API may use it.
 */
public final class AgentDisputeSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private AgentDisputeSpecifications() {}

    @SuppressWarnings("unchecked")
    public static Specification<Dispute> search(AgentDisputeSearch search) {
        return (root, query, cb) -> {
            // The merchant name is shown in every row and searched, so the transaction comes with the page. The count
            // query selects only a number, where a fetch is not allowed; a plain join serves the filter there.
            boolean countQuery = Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType());
            Join<Dispute, BankTransaction> transaction = countQuery
                    ? root.join("transaction")
                    : (Join<Dispute, BankTransaction>) (Fetch<?, ?>) root.fetch("transaction");

            List<Predicate> predicates = new ArrayList<>();
            switch (search.queue()) {
                case UNASSIGNED -> predicates.add(cb.isNull(root.get("assignedAgentId")));
                case MINE -> predicates.add(cb.equal(root.get("assignedAgentId"), search.agentId()));
                case ALL -> {}
            }
            if (!search.statuses().isEmpty()) {
                predicates.add(root.get("status").in(search.statuses()));
            }
            if (search.text() != null) {
                String pattern =
                        "%" + TransactionSpecifications.escapeLike(search.text().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("reference")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(transaction.get("merchantName")), pattern, LIKE_ESCAPE),
                        cb.exists(customerNameMatches(root, query.subquery(Integer.class), cb, pattern))));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * The dispute keeps only the customer's id, so the name is matched with a subquery instead of mapping the user as
     * an association that every other query would then have to avoid loading.
     */
    private static Subquery<Integer> customerNameMatches(
            Root<Dispute> dispute, Subquery<Integer> subquery, CriteriaBuilder cb, String pattern) {
        Root<AppUser> customer = subquery.from(AppUser.class);
        return subquery.select(cb.literal(1))
                .where(
                        cb.equal(customer.get("id"), dispute.get("customerId")),
                        cb.like(cb.lower(customer.get("displayName")), pattern, LIKE_ESCAPE));
    }
}
