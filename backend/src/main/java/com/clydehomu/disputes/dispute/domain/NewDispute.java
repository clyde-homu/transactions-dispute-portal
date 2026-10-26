package com.clydehomu.disputes.dispute.domain;

import com.clydehomu.disputes.transaction.domain.BankTransaction;
import java.math.BigDecimal;
import java.util.UUID;

/** Everything the state machine needs to open a dispute; the currency is always the transaction's. */
public record NewDispute(
        BankTransaction transaction,
        UUID customerId,
        DisputeReason reason,
        BigDecimal amount,
        String description,
        String reference) {}
