package com.clydehomu.disputes.transaction.api;

import com.clydehomu.disputes.shared.web.ApiFormats;
import com.clydehomu.disputes.transaction.application.TransactionWithDispute;
import com.clydehomu.disputes.transaction.domain.BankTransaction;
import com.clydehomu.disputes.transaction.domain.TransactionDirection;
import com.clydehomu.disputes.transaction.domain.TransactionStatus;
import java.time.Instant;
import java.util.UUID;

public record TransactionSummaryResponse(
        UUID id,
        String reference,
        Instant postedAt,
        String amount,
        String currency,
        TransactionDirection direction,
        TransactionStatus status,
        String merchantName,
        String disputeStatus) {

    public static TransactionSummaryResponse from(TransactionWithDispute view) {
        return from(
                view.transaction(),
                view.dispute() == null ? null : view.dispute().getStatus());
    }

    /** Used by the dispute feature, which already knows the current dispute status of the transaction. */
    public static TransactionSummaryResponse from(BankTransaction transaction, String disputeStatus) {
        return new TransactionSummaryResponse(
                transaction.getId(),
                transaction.getReference(),
                ApiFormats.timestamp(transaction.getPostedAt()),
                ApiFormats.money(transaction.getAmount()),
                transaction.getCurrency(),
                transaction.getDirection(),
                transaction.getStatus(),
                transaction.getMerchantName(),
                disputeStatus);
    }
}
