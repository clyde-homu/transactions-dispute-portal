package com.clydehomu.disputes.transaction.domain;

/** Why a transaction cannot be disputed. The constants are listed in the order the rules are checked. */
public enum EligibilityReason {
    NOT_A_DEBIT,
    PENDING,
    TOO_OLD,
    ALREADY_DISPUTED
}
