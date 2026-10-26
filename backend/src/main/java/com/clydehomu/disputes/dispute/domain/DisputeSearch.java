package com.clydehomu.disputes.dispute.domain;

import java.util.Set;

/** Filters for a customer's dispute history. An empty status set and a null text each mean "no filter". */
public record DisputeSearch(Set<DisputeStatus> statuses, String text) {}
