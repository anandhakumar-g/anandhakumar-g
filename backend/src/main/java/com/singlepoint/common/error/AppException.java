package com.singlepoint.common.error;

import org.springframework.http.HttpStatus;

/** Base type for all deliberately-raised application errors. */
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode, String message) {
        super(message != null ? message : errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public AppException(ErrorCode errorCode, String message, Throwable cause) {
        super(message != null ? message : errorCode.defaultMessage(), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() { return errorCode; }
    public String code() { return errorCode.code(); }
    public HttpStatus status() { return errorCode.status(); }

    // ---- convenience factories -------------------------------------------------

    public static AppException notFound(String what) {
        return new AppException(ErrorCode.NOT_FOUND, what + " not found");
    }

    public static AppException badRequest(String message) {
        return new AppException(ErrorCode.BAD_REQUEST, message);
    }

    public static AppException conflict(String message) {
        return new AppException(ErrorCode.CONFLICT, message);
    }

    public static AppException forbidden(String message) {
        return new AppException(ErrorCode.FORBIDDEN, message);
    }

    public static AppException unprocessable(String message) {
        return new AppException(ErrorCode.UNPROCESSABLE, message);
    }

    public static AppException illegalTransition(String message) {
        return new AppException(ErrorCode.ILLEGAL_TRANSITION, message);
    }
}
