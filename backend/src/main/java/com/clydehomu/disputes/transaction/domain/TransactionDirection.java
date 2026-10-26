package com.clydehomu.disputes.transaction.domain;

/** Amounts are always positive; the direction says whether money left or entered the account. */
public enum TransactionDirection {
    DEBIT,
    CREDIT
}
