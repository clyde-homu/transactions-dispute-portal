package com.clydehomu.disputes.dispute.application;

import com.clydehomu.disputes.dispute.domain.Dispute;
import com.clydehomu.disputes.dispute.domain.DisputeStatus;

/**
 * A dispute ready for the API: the entity with its transaction and timeline already loaded, plus the status of the
 * transaction's current (non-withdrawn) dispute. For a withdrawn dispute that is usually null, but a newer dispute on
 * the same transaction would show up here.
 */
public record DisputeView(Dispute dispute, DisputeStatus transactionDisputeStatus) {}
