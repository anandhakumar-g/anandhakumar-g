package com.singlepoint.common.error;

import org.springframework.http.HttpStatus;

/** Stable error codes returned to clients. Format: SP-&lt;http-status&gt;[-TAG]. */
public enum ErrorCode {

    VALIDATION_FAILED       ("SP-400-VALIDATION",   HttpStatus.BAD_REQUEST,           "Request validation failed"),
    BAD_REQUEST             ("SP-400",              HttpStatus.BAD_REQUEST,           "Bad request"),
    OTP_INVALID             ("SP-400-OTP",          HttpStatus.BAD_REQUEST,           "Invalid or expired verification code"),
    UNAUTHENTICATED         ("SP-401",              HttpStatus.UNAUTHORIZED,          "Authentication required"),
    TOKEN_EXPIRED           ("SP-401-EXPIRED",      HttpStatus.UNAUTHORIZED,          "Session expired, sign in again"),
    FORBIDDEN               ("SP-403",              HttpStatus.FORBIDDEN,             "Not allowed"),
    TENANT_SCOPE_VIOLATION  ("SP-403-TENANT",       HttpStatus.FORBIDDEN,             "Resource belongs to another community"),
    NOT_FOUND              ("SP-404",              HttpStatus.NOT_FOUND,             "Not found"),
    METHOD_NOT_ALLOWED      ("SP-405",              HttpStatus.METHOD_NOT_ALLOWED,    "Method not allowed"),
    CONFLICT               ("SP-409",              HttpStatus.CONFLICT,              "Conflict"),
    ILLEGAL_TRANSITION      ("SP-409-TRANSITION",   HttpStatus.CONFLICT,             "Illegal status transition"),
    PAYMENT_REQUIRED       ("SP-402",              HttpStatus.PAYMENT_REQUIRED,      "Payment required"),
    SUBSCRIPTION_LAPSED    ("SP-402-SUBSCRIPTION", HttpStatus.PAYMENT_REQUIRED,      "Subscription has lapsed"),
    QUOTA_EXCEEDED         ("SP-402-QUOTA",        HttpStatus.PAYMENT_REQUIRED,      "Plan limit reached"),
    UNPROCESSABLE          ("SP-422",              HttpStatus.UNPROCESSABLE_ENTITY,  "Request could not be processed"),
    PROVIDER_NOT_ASSIGNABLE ("SP-422-PROVIDER",     HttpStatus.UNPROCESSABLE_ENTITY,  "Service provider is not verified/active"),
    KYC_INCOMPLETE          ("SP-422-KYC",          HttpStatus.UNPROCESSABLE_ENTITY,  "Required KYC documents are not accepted yet"),
    OFFER_LIMIT_REACHED     ("SP-409-OFFER",        HttpStatus.CONFLICT,              "Offer redemption limit reached"),
    RATE_LIMITED           ("SP-429",              HttpStatus.TOO_MANY_REQUESTS,     "Too many requests, try again later"),
    INTERNAL               ("SP-500",              HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong"),
    STORAGE_ERROR          ("SP-500-STORAGE",      HttpStatus.INTERNAL_SERVER_ERROR, "File storage error"),
    NOT_IMPLEMENTED        ("SP-501",              HttpStatus.NOT_IMPLEMENTED,       "Not available yet");

    private final String code;
    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(String code, HttpStatus status, String defaultMessage) {
        this.code = code;
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public String code() { return code; }
    public HttpStatus status() { return status; }
    public String defaultMessage() { return defaultMessage; }
}
