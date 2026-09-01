package com.singlepoint.common.util;

import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;

import java.util.regex.Pattern;

/** Minimal phone normalisation to a canonical +&lt;country&gt;&lt;national&gt; form (India default). */
public final class PhoneNumbers {

    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final String DEFAULT_CC = "+91";

    private PhoneNumbers() { }

    public static String normalize(String raw) {
        if (raw == null) throw new AppException(ErrorCode.VALIDATION_FAILED, "phone is required");
        String s = raw.trim().replaceAll("[\\s\\-()]", "");
        if (s.startsWith("00")) s = "+" + s.substring(2);
        if (s.startsWith("+")) {
            String rest = s.substring(1);
            if (!DIGITS.matcher(rest).matches() || rest.length() < 8 || rest.length() > 15) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "invalid phone number");
            }
            return "+" + rest;
        }
        if (s.startsWith("0")) s = s.substring(1);
        if (!DIGITS.matcher(s).matches() || s.length() != 10) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "invalid phone number");
        }
        return DEFAULT_CC + s;
    }

    /** Last-4-visible mask, e.g. +91******7788. */
    public static String mask(String normalized) {
        if (normalized == null || normalized.length() < 4) return "****";
        String last4 = normalized.substring(normalized.length() - 4);
        return "••••••" + last4;
    }
}
