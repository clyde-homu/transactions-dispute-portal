package com.clydehomu.disputes.transaction.application;

import java.time.LocalDate;

/**
 * What the customer asked for when listing an account's transactions. Dates are calendar days in the bank's time zone;
 * the paging values have already been range-checked at the API edge.
 */
public record TransactionQuery(LocalDate from, LocalDate to, String text, boolean disputedOnly, int page, int size) {}
