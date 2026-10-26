package com.clydehomu.disputes.shared.error;

/** One entry of the {@code errors} array on a {@code VALIDATION_FAILED} problem. */
public record FieldViolation(String field, String message) {}
