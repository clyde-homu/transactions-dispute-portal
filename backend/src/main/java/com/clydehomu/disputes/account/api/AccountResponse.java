package com.clydehomu.disputes.account.api;

import com.clydehomu.disputes.account.domain.Account;
import com.clydehomu.disputes.shared.web.ApiFormats;
import java.util.UUID;

public record AccountResponse(
        UUID id, String maskedNumber, String productName, String currency, String availableBalance) {

    static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getMaskedNumber(),
                account.getProductName(),
                account.getCurrency(),
                ApiFormats.money(account.getAvailableBalance()));
    }
}
