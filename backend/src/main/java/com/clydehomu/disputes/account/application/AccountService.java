package com.clydehomu.disputes.account.application;

import com.clydehomu.disputes.account.domain.Account;
import com.clydehomu.disputes.account.infrastructure.AccountRepository;
import com.clydehomu.disputes.shared.error.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public List<Account> listForCustomer(UUID customerId) {
        return accounts.findByOwnerIdOrderByProductNameAsc(customerId);
    }

    /**
     * Another customer's account and a missing one are the same answer, so account ids cannot be probed.
     */
    @Transactional(readOnly = true)
    public void requireOwnedBy(UUID customerId, UUID accountId) {
        if (!accounts.existsByIdAndOwnerId(accountId, customerId)) {
            throw new NotFoundException("The requested resource was not found.");
        }
    }
}
