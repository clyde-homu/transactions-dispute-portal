package com.clydehomu.disputes.shared.error;

import java.util.List;

/**
 * A rule that can only be checked after the request has been bound, such as "from must not be after to" or "amount must
 * not exceed the transaction". It is reported exactly like a Bean Validation failure, with the failing fields listed.
 */
public class ValidationFailedException extends ApiException {

    private final List<FieldViolation> violations;

    public ValidationFailedException(String field, String message) {
        this(List.of(new FieldViolation(field, message)));
    }

    public ValidationFailedException(List<FieldViolation> violations) {
        super(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.");
        this.violations = List.copyOf(violations);
    }

    public List<FieldViolation> getViolations() {
        return violations;
    }
}
