package com.clydehomu.disputes.shared.error;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;

/**
 * Turns every failure into an RFC 9457 problem document with a stable {@code code}. Framework exceptions are handled by
 * the parent class; this class adds the {@code code} member to them and covers the application's own exceptions plus a
 * catch-all that never exposes internals.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // The contract lists `type` as a required member, so it is always written, even though RFC 9457 allows omitting it.
    private static final URI ABOUT_BLANK = URI.create("about:blank");
    private static final String CODE = "code";
    private static final String ERRORS = "errors";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("Request failed with {}", ex.getCode(), ex);
        }
        return problem(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(RateLimitedException.class)
    public ResponseEntity<ProblemDetail> handleRateLimited(RateLimitedException ex) {
        // Whole seconds, rounded up, and never zero: "retry in 0 s" would invite an immediate second refusal.
        long seconds = Math.max(1, (ex.getRetryAfter().toMillis() + 999) / 1000);
        return ResponseEntity.status(ex.getStatus())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemBody(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(ValidationFailedException.class)
    public ResponseEntity<ProblemDetail> handleValidationFailed(ValidationFailedException ex) {
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(validationBody(ex.getViolations()));
    }

    /**
     * Method security throws this from inside the controller call, where a catch-all would otherwise turn it into a
     * 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return problem(ErrorCode.FORBIDDEN, "You are not allowed to perform this action.");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        return problem(ErrorCode.UNAUTHENTICATED, "Sign in to continue.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(GlobalExceptionHandler::toViolation)
                .toList();
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(validationBody(violations));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        // The full cause goes to the log (tagged with the request id); the client only gets a generic message.
        log.error("Unhandled exception", ex);
        return problem(ErrorCode.INTERNAL_ERROR, "Something went wrong on our side. Please try again.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getAllErrors().stream()
                .map(GlobalExceptionHandler::toViolation)
                .toList();
        return handleExceptionInternal(ex, validationBody(violations), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(
                                fieldName(requestName(result.getMethodParameter()), error), messageOf(error))))
                .toList();
        return handleExceptionInternal(ex, validationBody(violations), headers, status, request);
    }

    /** A path or query value of the wrong type (a malformed UUID, "page=abc") names the offending parameter. */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String field =
                ex instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName() : ex.getPropertyName();
        List<FieldViolation> violations =
                field == null ? List.of() : List.of(new FieldViolation(field, "has an invalid value"));
        return handleExceptionInternal(ex, validationBody(violations), headers, status, request);
    }

    /** A body that cannot be read names the offending property when Jackson knows it, and nothing else about the parser. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(ex, validationBody(violationsFromBody(ex)), headers, status, request);
    }

    /**
     * Every response built by the parent class passes through here, so this is the one place that guarantees the
     * problem content type and the {@code code} member.
     */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail detail) {
            detail.setType(ABOUT_BLANK);
            if (detail.getProperties() == null || !detail.getProperties().containsKey(CODE)) {
                detail.setProperty(CODE, ErrorCode.forStatus(statusCode).name());
            }
            if (statusCode.value() == 404) {
                // The parent's text names the missing static resource; the contract promises one neutral sentence.
                detail.setDetail("The requested resource was not found.");
            }
            HttpHeaders problemHeaders = new HttpHeaders();
            problemHeaders.putAll(headers);
            problemHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
            return super.createResponseEntity(body, problemHeaders, statusCode, request);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private static ResponseEntity<ProblemDetail> problem(ErrorCode code, String detail) {
        return ResponseEntity.status(code.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemBody(code, detail));
    }

    private static ProblemDetail problemBody(ErrorCode code, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(code.status(), detail);
        body.setType(ABOUT_BLANK);
        body.setProperty(CODE, code.name());
        return body;
    }

    private static ProblemDetail validationBody(List<FieldViolation> violations) {
        // A stable order keeps responses deterministic for clients and tests.
        List<FieldViolation> sorted = violations.stream()
                .sorted(Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::message))
                .toList();
        ProblemDetail body = problemBody(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.");
        body.setProperty(ERRORS, sorted);
        return body;
    }

    private static List<FieldViolation> violationsFromBody(HttpMessageNotReadableException ex) {
        for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof JacksonException jackson && !jackson.getPath().isEmpty()) {
                StringBuilder field = new StringBuilder();
                for (JacksonException.Reference step : jackson.getPath()) {
                    if (step.getPropertyName() != null) {
                        field.append(field.isEmpty() ? "" : ".").append(step.getPropertyName());
                    } else {
                        field.append('[').append(step.getIndex()).append(']');
                    }
                }
                return List.of(new FieldViolation(field.toString(), "has an invalid value"));
            }
        }
        return List.of();
    }

    private static FieldViolation toViolation(ObjectError error) {
        String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
        return new FieldViolation(field, messageOf(error));
    }

    private static FieldViolation toViolation(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        // Method validation prefixes paths with "method.argument"; clients only know the argument name.
        String field = path.substring(path.lastIndexOf('.') + 1);
        return new FieldViolation(field, violation.getMessage());
    }

    /** The name the client used: the @RequestParam or @PathVariable name when given, else the Java parameter name. */
    private static String requestName(MethodParameter parameter) {
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null) {
            String name = requestParam.name().isEmpty() ? requestParam.value() : requestParam.name();
            if (!name.isEmpty()) {
                return name;
            }
        }
        PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null) {
            String name = pathVariable.name().isEmpty() ? pathVariable.value() : pathVariable.name();
            if (!name.isEmpty()) {
                return name;
            }
        }
        return parameter.getParameterName();
    }

    private static String fieldName(String parameterName, MessageSourceResolvable error) {
        return error instanceof FieldError fieldError ? fieldError.getField() : String.valueOf(parameterName);
    }

    private static String messageOf(MessageSourceResolvable error) {
        String message = error.getDefaultMessage();
        return message == null ? "is invalid" : message;
    }
}
