package com.clydehomu.disputes.dispute.api;

import com.clydehomu.disputes.dispute.domain.DisputeReason;
import com.clydehomu.disputes.dispute.domain.ReasonCategory;
import java.util.Arrays;
import java.util.List;

/** One group of the "raise a dispute" stepper: a category and the reasons in it. */
public record ReasonCategoryResponse(ReasonCategory category, String label, List<ReasonResponse> reasons) {

    public record ReasonResponse(DisputeReason code, String label) {}

    /** Every category in display order, each with its reasons in catalogue order. */
    static List<ReasonCategoryResponse> catalogue() {
        return Arrays.stream(ReasonCategory.values())
                .map(category -> new ReasonCategoryResponse(
                        category,
                        category.label(),
                        category.reasons().stream()
                                .map(reason -> new ReasonResponse(reason, reason.label()))
                                .toList()))
                .toList();
    }
}
