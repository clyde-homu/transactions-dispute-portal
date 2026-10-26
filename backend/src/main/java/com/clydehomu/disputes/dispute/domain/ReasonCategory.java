package com.clydehomu.disputes.dispute.domain;

import java.util.Arrays;
import java.util.List;

/** How dispute reasons are grouped on screen, in display order. The grouping mirrors how card schemes sort disputes. */
public enum ReasonCategory {
    FRAUD("Fraud"),
    AUTHORISATION("Authorisation"),
    PROCESSING_ERROR("Processing error"),
    CONSUMER("Goods or services");

    private final String label;

    ReasonCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** The reasons in this category, in catalogue order. */
    public List<DisputeReason> reasons() {
        return Arrays.stream(DisputeReason.values())
                .filter(reason -> reason.category() == this)
                .toList();
    }
}
