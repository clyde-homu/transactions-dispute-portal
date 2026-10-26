package com.clydehomu.disputes.dispute.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The reason catalogue and the reference format, which the stepper and the confirmation screen rely on. */
class DisputeCatalogueTest {

    @Test
    void categoriesComeInDisplayOrderWithTheContractLabels() {
        assertThat(ReasonCategory.values())
                .extracting(ReasonCategory::name, ReasonCategory::label)
                .containsExactly(
                        tuple("FRAUD", "Fraud"),
                        tuple("AUTHORISATION", "Authorisation"),
                        tuple("PROCESSING_ERROR", "Processing error"),
                        tuple("CONSUMER", "Goods or services"));
    }

    @Test
    void theEightReasonsKeepTheirCodesWordingAndGrouping() {
        assertThat(DisputeReason.values())
                .extracting(DisputeReason::name, r -> r.category().name(), DisputeReason::label)
                .containsExactly(
                        tuple("F01", "FRAUD", "I didn't make this transaction"),
                        tuple("F02", "FRAUD", "My card was lost or stolen"),
                        tuple("A01", "AUTHORISATION", "I didn't authorise this debit order"),
                        tuple("P01", "PROCESSING_ERROR", "I was charged twice"),
                        tuple("P02", "PROCESSING_ERROR", "The amount is wrong"),
                        tuple("C01", "CONSUMER", "I didn't receive the goods or service"),
                        tuple("C02", "CONSUMER", "I cancelled, but I'm still being charged"),
                        tuple("C03", "CONSUMER", "My refund never arrived"));
    }

    @Test
    void everyCategoryHasAtLeastOneReasonAndEveryReasonIsInExactlyOneCategory() {
        for (ReasonCategory category : ReasonCategory.values()) {
            assertThat(category.reasons()).isNotEmpty().allMatch(reason -> reason.category() == category);
        }
        int total = 0;
        for (ReasonCategory category : ReasonCategory.values()) {
            total += category.reasons().size();
        }
        assertThat(total).isEqualTo(DisputeReason.values().length);
    }

    @ParameterizedTest(name = "{0} / {1} -> {2}")
    @CsvSource({
        "2026-10-26T07:00:00Z, 1, DSP-2026-000001",
        "2026-10-26T07:00:00Z, 123, DSP-2026-000123",
        "2026-10-26T07:00:00Z, 999999, DSP-2026-999999",
        "2026-10-26T07:00:00Z, 1000000, DSP-2026-1000000",
        "2025-12-31T21:59:59Z, 7, DSP-2025-000007",
        // 22:00 UTC is already midnight in Johannesburg, so the new year has started there.
        "2025-12-31T22:00:00Z, 8, DSP-2026-000008"
    })
    void referencesUseTheBankCalendarYearAndPadTheSequenceToSixDigits(
            String createdAt, long sequence, String expected) {
        assertThat(DisputeReference.format(Instant.parse(createdAt), sequence)).isEqualTo(expected);
    }
}
