package com.clydehomu.disputes.shared.error;

/**
 * The resource does not exist or belongs to someone else. Both cases look identical to the caller so ownership is never
 * revealed.
 */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }
}
