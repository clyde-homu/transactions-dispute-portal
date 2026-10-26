package com.clydehomu.disputes.shared.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** How values are written on the wire, in one place so every endpoint agrees with the contract. */
public final class ApiFormats {

    private ApiFormats() {}

    /** Money is a string with exactly two decimals ("1250.00"), rounded half-even; never a JSON number. */
    public static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }

    /** Timestamps go out to the second, as in the contract examples; storage keeps the full precision. */
    public static Instant timestamp(Instant instant) {
        return instant.truncatedTo(ChronoUnit.SECONDS);
    }
}
