package com.singlepoint.auth.sms;

import com.singlepoint.common.util.PhoneNumbers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Dev / MVP-1 SMS sender: writes the OTP to the application log instead of sending an SMS.
 * A real provider (e.g. Msg91SmsSender, @Profile("cloud")) replaces this later.
 */
@Component
@Profile("!cloud")
public class LoggingSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

    @Override
    public void sendOtp(String phoneE164, String code, int ttlSeconds) {
        log.info("=== DEV OTP === phone={} code={} (valid {}s)", PhoneNumbers.mask(phoneE164), code, ttlSeconds);
    }
}
