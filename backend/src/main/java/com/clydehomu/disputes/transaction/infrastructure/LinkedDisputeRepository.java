package com.clydehomu.disputes.transaction.infrastructure;

import com.clydehomu.disputes.transaction.domain.LinkedDispute;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface LinkedDisputeRepository extends Repository<LinkedDispute, UUID> {

    /**
     * One query for a whole page of transactions, newest dispute first, so the caller keeps the first one per
     * transaction.
     */
    @Query("""
            select d from LinkedDispute d
            where d.transactionId in :transactionIds and d.status <> 'WITHDRAWN'
            order by d.createdAt desc, d.id
            """)
    List<LinkedDispute> findActiveByTransactionIds(Collection<UUID> transactionIds);
}
