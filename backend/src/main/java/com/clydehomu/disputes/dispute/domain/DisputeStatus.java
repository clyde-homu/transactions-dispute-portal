package com.clydehomu.disputes.dispute.domain;

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
}
