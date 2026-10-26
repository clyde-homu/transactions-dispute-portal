package com.clydehomu.disputes.account.infrastructure;

import com.clydehomu.disputes.account.domain.Account;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    /** The ownership filter lives in the query, so another customer's accounts can never be loaded by mistake. */
    List<Account> findByOwnerIdOrderByProductNameAsc(UUID ownerId);
}
