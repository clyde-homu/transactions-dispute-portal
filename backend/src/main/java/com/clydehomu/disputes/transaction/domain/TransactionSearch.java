package com.clydehomu.disputes.transaction.domain;

import java.time.Instant;

/**
 * Filters for one account's transaction list. The date bounds are already instants (start of the first day, start of the
 * day after the last day), so the query is a plain half-open range; a null bound means "no limit".
 */
public record TransactionSearch(Instant postedFrom, Instant postedBefore, String text, boolean disputedOnly) {}
