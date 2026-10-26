package com.clydehomu.disputes.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Base type for errors that features raise on purpose. The status comes from the {@link ErrorCode}; the message is sent
 * to API clients as the problem {@code detail}, so it must never contain internals such as SQL or class names.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ApiException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return code.status();
    }
}
