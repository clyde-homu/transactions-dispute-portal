package com.clydehomu.disputes.dispute.domain;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Where a dispute is in its life. The first three are open; the others are final. */
public enum DisputeStatus {
    SUBMITTED(true),
    UNDER_REVIEW(true),
    AWAITING_CUSTOMER_INFO(true),
    RESOLVED_UPHELD(false),
    RESOLVED_DECLINED(false),
    WITHDRAWN(false);

    private final boolean open;

    DisputeStatus(boolean open) {
        this.open = open;
    }

    /** Open disputes can still be worked on or withdrawn. */
    public boolean isOpen() {
        return open;
    }

    public boolean isClosed() {
        return !open;
    }

    /** Every open status, for queries that need the same rule as {@link #isOpen()} in a form a database can filter on. */
    public static Set<DisputeStatus> openStatuses() {
        return Arrays.stream(values())
                .filter(DisputeStatus::isOpen)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DisputeStatus.class)));
    }
}
