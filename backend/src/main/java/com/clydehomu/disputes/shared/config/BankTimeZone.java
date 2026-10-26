package com.clydehomu.disputes.shared.config;

import java.time.ZoneId;

/** The bank's calendar: date filters and reference years are read in South African time, while storage stays UTC. */
public final class BankTimeZone {

    public static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private BankTimeZone() {}
}
