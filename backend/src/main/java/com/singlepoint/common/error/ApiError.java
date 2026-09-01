package com.singlepoint.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/** Uniform error body. Success responses never use this shape. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    private final boolean success = false;
    private final String errorCode;
    private final String message;
    private final String requestId;
    private final String timestamp;
    private final List<FieldViolation> fieldErrors;

    public ApiError(String errorCode, String message, String requestId, List<FieldViolation> fieldErrors) {
        this.errorCode = errorCode;
        this.message = message;
        this.requestId = requestId;
        this.timestamp = Instant.now().toString();
        this.fieldErrors = (fieldErrors == null || fieldErrors.isEmpty()) ? null : fieldErrors;
    }

    public boolean isSuccess() { return success; }
    public String getErrorCode() { return errorCode; }
    public String getMessage() { return message; }
    public String getRequestId() { return requestId; }
    public String getTimestamp() { return timestamp; }
    public List<FieldViolation> getFieldErrors() { return fieldErrors; }

    public static class FieldViolation {
        private final String field;
        private final String message;

        public FieldViolation(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String getField() { return field; }
        public String getMessage() { return message; }
    }
}
