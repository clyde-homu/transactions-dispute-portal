package com.clydehomu.disputes.transaction.application;

import com.clydehomu.disputes.transaction.domain.BankTransaction;
import com.clydehomu.disputes.transaction.domain.LinkedDispute;

/** A transaction together with its current (non-withdrawn) dispute, if it has one. */
public record TransactionWithDispute(BankTransaction transaction, LinkedDispute dispute) {}
