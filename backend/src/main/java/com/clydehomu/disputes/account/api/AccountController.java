package com.clydehomu.disputes.account.api;

import com.clydehomu.disputes.account.application.AccountService;
import com.clydehomu.disputes.auth.application.AuthenticatedUser;
import com.clydehomu.disputes.shared.security.CustomerOnly;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
@CustomerOnly
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return accountService.listForCustomer(user.getId()).stream()
                .map(AccountResponse::from)
                .toList();
    }
}
