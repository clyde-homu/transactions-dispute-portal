package com.clydehomu.disputes.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

class ErrorCodeTest {

    @ParameterizedTest
    @CsvSource({
        "VALIDATION_FAILED, 400",
        "BAD_CREDENTIALS, 401",
        "UNAUTHENTICATED, 401",
        "FORBIDDEN, 403",
        "NOT_FOUND, 404",
        "TRANSACTION_NOT_ELIGIBLE, 422",
        "DISPUTE_ALREADY_EXISTS, 409",
        "INVALID_DISPUTE_TRANSITION, 409",
        "TOO_MANY_LOGIN_ATTEMPTS, 429",
        "INTERNAL_ERROR, 500"
    })
    void contractCodesMapToTheirDocumentedStatus(ErrorCode code, int expectedStatus) {
        assertThat(code.status().value()).isEqualTo(expectedStatus);
    }

    @ParameterizedTest
    @CsvSource({
        "400, VALIDATION_FAILED",
        "401, UNAUTHENTICATED",
        "403, FORBIDDEN",
        "404, NOT_FOUND",
        "405, METHOD_NOT_ALLOWED",
        "406, NOT_ACCEPTABLE",
        "415, UNSUPPORTED_MEDIA_TYPE",
        "413, CLIENT_ERROR",
        "500, INTERNAL_ERROR",
        "503, INTERNAL_ERROR"
    })
    void frameworkStatusesMapToACode(int status, ErrorCode expected) {
        assertThat(ErrorCode.forStatus(HttpStatusCode.valueOf(status))).isEqualTo(expected);
    }

    @Test
    void apiExceptionDerivesItsStatusFromTheCode() {
        ApiException exception = new ApiException(ErrorCode.DISPUTE_ALREADY_EXISTS, "Already disputed.");

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getCode()).isEqualTo(ErrorCode.DISPUTE_ALREADY_EXISTS);
        assertThat(exception).hasMessage("Already disputed.");
    }

    @Test
    void notFoundExceptionIsA404() {
        NotFoundException exception = new NotFoundException("Transaction not found.");

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getCode()).isEqualTo(ErrorCode.NOT_FOUND);
    }
}
