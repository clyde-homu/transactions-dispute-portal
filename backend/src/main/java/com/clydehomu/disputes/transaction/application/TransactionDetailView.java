package com.clydehomu.disputes.transaction.application;

import com.clydehomu.disputes.transaction.domain.Eligibility;

public record TransactionDetailView(TransactionWithDispute transaction, Eligibility eligibility) {}
