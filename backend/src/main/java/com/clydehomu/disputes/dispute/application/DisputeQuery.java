package com.clydehomu.disputes.dispute.application;

import com.clydehomu.disputes.dispute.domain.DisputeStatus;
import java.util.Set;

/** What the customer asked for when listing their disputes; the paging values are range-checked at the API edge. */
public record DisputeQuery(Set<DisputeStatus> statuses, String text, int page, int size) {}
