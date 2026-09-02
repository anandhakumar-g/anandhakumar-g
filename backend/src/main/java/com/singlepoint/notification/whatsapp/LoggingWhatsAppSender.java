package com.singlepoint.notification.whatsapp;

import com.singlepoint.common.util.PhoneNumbers;
import com.singlepoint.notification.WhatsAppSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Dev / non-cloud WhatsApp sender: writes the message to the log instead of calling a provider. */
@Component
@Profile("!cloud")
public class LoggingWhatsAppSender implements WhatsAppSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingWhatsAppSender.class);

    @Override
    public boolean send(String phoneE164, String title, String body, Map<String, Object> data) {
        log.info("=== DEV WHATSAPP === to={} | {} — {}", PhoneNumbers.mask(phoneE164), title, body);
        return true;
    }
}
