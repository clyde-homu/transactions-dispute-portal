package com.clydehomu.disputes.dispute.infrastructure;

import com.clydehomu.disputes.dispute.domain.Dispute;
import com.clydehomu.disputes.dispute.domain.DisputeStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface DisputeRepository extends JpaRepository<Dispute, UUID>, JpaSpecificationExecutor<Dispute> {

    /**
     * One dispute with its transaction and timeline in a single query. Ownership is part of the query: another
     * customer's dispute simply is not found.
     */
    @Query("""
            select distinct d from Dispute d
            join fetch d.transaction
            left join fetch d.events
            where d.id = :id and d.customerId = :customerId
            """)
    Optional<Dispute> findDetailById(UUID id, UUID customerId);

    /**
     * Locks the row so two withdrawals of the same dispute run one after the other; the second then sees the new status
     * and is refused by the state machine instead of failing on the version check. No fetch joins here, because a lock
     * combined with an outer join is not portable.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Dispute d where d.id = :id and d.customerId = :customerId")
    Optional<Dispute> findOwnedByIdForUpdate(UUID id, UUID customerId);

    boolean existsByCustomerId(UUID customerId);

    @Query("select d.reference from Dispute d where d.transaction.id = :transactionId and d.status <> :excluded")
    List<String> findReferencesByTransactionIdExcludingStatus(UUID transactionId, DisputeStatus excluded);

    @Query("select d.status from Dispute d where d.transaction.id = :transactionId and d.status <> :excluded")
    List<DisputeStatus> findStatusesByTransactionIdExcludingStatus(UUID transactionId, DisputeStatus excluded);

    /** The reference of the transaction's dispute that is not withdrawn, if it has one. */
    default Optional<String> findActiveReference(UUID transactionId) {
        return findReferencesByTransactionIdExcludingStatus(transactionId, DisputeStatus.WITHDRAWN).stream()
                .findFirst();
    }

    /** The status of the transaction's dispute that is not withdrawn, if it has one. */
    default Optional<DisputeStatus> findActiveStatus(UUID transactionId) {
        return findStatusesByTransactionIdExcludingStatus(transactionId, DisputeStatus.WITHDRAWN).stream()
                .findFirst();
    }

    /**
     * Next value of the global reference sequence. Plain SQL that PostgreSQL and H2 (in PostgreSQL mode) both run, so
     * standalone mode needs no special case.
     */
    @Query(value = "select nextval('dispute_reference_seq')", nativeQuery = true)
    long nextReferenceNumber();
}
