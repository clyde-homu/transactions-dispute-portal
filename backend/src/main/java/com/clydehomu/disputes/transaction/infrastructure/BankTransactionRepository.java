package com.clydehomu.disputes.transaction.infrastructure;

import com.clydehomu.disputes.transaction.domain.BankTransaction;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, UUID> {

    long countByAccountId(UUID accountId);
}
