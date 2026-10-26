package com.clydehomu.disputes.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class ApiFormatsTest {

    @ParameterizedTest
    @CsvSource({
        "1250.0000, 1250.00",
        "1250, 1250.00",
        "189.9, 189.90",
        "0.125, 0.12",
        "0.135, 0.14",
        "-18452.3749, -18452.37",
        "999999999999999.99, 999999999999999.99"
    })
    void moneyHasExactlyTwoDecimalsRoundedHalfEven(String stored, String expected) {
        assertThat(ApiFormats.money(new BigDecimal(stored))).isEqualTo(expected);
    }

    @Test
    void moneyNeverUsesScientificNotation() {
        assertThat(ApiFormats.money(new BigDecimal("1E+3"))).isEqualTo("1000.00");
    }

    @Test
    void timestampsAreTruncatedToTheSecond() {
        assertThat(ApiFormats.timestamp(Instant.parse("2026-10-24T09:14:22.987654Z")))
                .isEqualTo(Instant.parse("2026-10-24T09:14:22Z"));
    }

    @Test
    void thePageEnvelopeCarriesTheContractFields() {
        PageImpl<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 83);

        PageResponse<String> response = PageResponse.of(page, String::toUpperCase);

        assertThat(response.items()).containsExactly("A", "B");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalItems()).isEqualTo(83);
        assertThat(response.totalPages()).isEqualTo(42);
    }
}
