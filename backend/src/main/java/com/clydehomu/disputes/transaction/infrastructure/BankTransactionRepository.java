package com.clydehomu.disputes.transaction.infrastructure;

import com.clydehomu.disputes.transaction.domain.BankTransaction;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface BankTransactionRepository
        extends JpaRepository<BankTransaction, UUID>, JpaSpecificationExecutor<BankTransaction> {

    long countByAccountId(UUID accountId);

    /** Ownership is part of the query: another customer's transaction simply is not found. */
    @Query("""
            select t from BankTransaction t
            where t.id = :id
              and exists (select 1 from Account a where a.id = t.account.id and a.ownerId = :customerId)
            """)
    Optional<BankTransaction> findOwnedById(UUID id, UUID customerId);
}
