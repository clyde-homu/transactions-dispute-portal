package com.clydehomu.disputes.dispute.domain;

import com.clydehomu.disputes.shared.config.BankTimeZone;
import java.time.Instant;

/** The human-friendly dispute number, {@code DSP-<year>-<sequence>}, for example {@code DSP-2026-000123}. */
public final class DisputeReference {

    private DisputeReference() {}

    /**
     * @param createdAt when the dispute was raised; its calendar year in the bank's time zone becomes the year part
     * @param sequence the next value of the global database sequence; it never resets, so references stay unique
     *     across years, and past 999999 it simply grows a digit
     */
    public static String format(Instant createdAt, long sequence) {
        int year = createdAt.atZone(BankTimeZone.ZONE).getYear();
        return "DSP-%d-%06d".formatted(year, sequence);
    }
}
