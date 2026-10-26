package com.clydehomu.disputes.auth.domain;

/** Only CUSTOMER can sign in to anything useful in this version; AGENT is reserved for the back office. */
public enum Role {
    CUSTOMER,
    AGENT
}
