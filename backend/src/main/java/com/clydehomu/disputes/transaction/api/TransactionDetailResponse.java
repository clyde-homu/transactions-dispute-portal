package com.clydehomu.disputes.transaction.api;

import com.clydehomu.disputes.shared.web.ApiFormats;
import com.clydehomu.disputes.transaction.application.TransactionDetailView;
import com.clydehomu.disputes.transaction.domain.BankTransaction;
import com.clydehomu.disputes.transaction.domain.Channel;
import com.clydehomu.disputes.transaction.domain.TransactionDirection;
import com.clydehomu.disputes.transaction.domain.TransactionStatus;
import java.time.Instant;
import java.util.UUID;

/** A transaction summary plus what the detail screen needs: category, channel, and whether it can be disputed. */
public record TransactionDetailResponse(
        UUID id,
        String reference,
        Instant postedAt,
        String amount,
        String currency,
        TransactionDirection direction,
        TransactionStatus status,
        String merchantName,
        String disputeStatus,
        String merchantCategory,
        Channel channel,
        EligibilityResponse eligibility,
        UUID disputeId) {

    static TransactionDetailResponse from(TransactionDetailView view) {
        BankTransaction transaction = view.transaction().transaction();
        var dispute = view.transaction().dispute();
        return new TransactionDetailResponse(
                transaction.getId(),
                transaction.getReference(),
                ApiFormats.timestamp(transaction.getPostedAt()),
                ApiFormats.money(transaction.getAmount()),
                transaction.getCurrency(),
                transaction.getDirection(),
                transaction.getStatus(),
                transaction.getMerchantName(),
                dispute == null ? null : dispute.getStatus(),
                transaction.getMerchantCategory(),
                transaction.getChannel(),
                EligibilityResponse.from(view.eligibility()),
                dispute == null ? null : dispute.getId());
    }
}
