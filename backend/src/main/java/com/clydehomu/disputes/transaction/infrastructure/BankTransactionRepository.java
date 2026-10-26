package com.clydehomu.disputes.transaction.infrastructure;

import com.clydehomu.disputes.transaction.domain.BankTransaction;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface BankTransactionRepository
        extends JpaRepository<BankTransaction, UUID>, JpaSpecificationExecutor<BankTransaction> {

    long countByAccountId(UUID accountId);

    List<BankTransaction> findByAccountId(UUID accountId);

    /** Ownership is part of the query: another customer's transaction simply is not found. */
    @Query("""
            select t from BankTransaction t
            where t.id = :id
              and exists (select 1 from Account a where a.id = t.account.id and a.ownerId = :customerId)
            """)
    Optional<BankTransaction> findOwnedById(UUID id, UUID customerId);

    /**
     * Same ownership rule, but takes a row lock held until the surrounding transaction ends. Raising a dispute uses it
     * to make "no open dispute yet, so insert one" atomic per transaction: a second request for the same transaction
     * waits here and then sees the first one's dispute. A unique constraint cannot do this job, because withdrawn
     * disputes may be followed by a new one and partial indexes are not portable to H2.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t from BankTransaction t
            where t.id = :id
              and exists (select 1 from Account a where a.id = t.account.id and a.ownerId = :customerId)
            """)
    Optional<BankTransaction> findOwnedByIdForUpdate(UUID id, UUID customerId);
}
