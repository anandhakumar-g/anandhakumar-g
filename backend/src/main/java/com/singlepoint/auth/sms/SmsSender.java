package com.singlepoint.auth.sms;

/** Transactional SMS delivery. MVP-1 uses {@link LoggingSmsSender}; real providers slot in behind this. */
public interface SmsSender {

    void sendOtp(String phoneE164, String code, int ttlSeconds);
}
