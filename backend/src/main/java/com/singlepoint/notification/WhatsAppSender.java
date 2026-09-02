package com.singlepoint.notification;

import java.util.Map;

/**
 * Delivers a notification over WhatsApp. Implemented as a profile split — a logging stub off
 * the {@code cloud} profile, a real provider on it (mirrors {@code SmsSender} / the payment
 * gateway).
 */
public interface WhatsAppSender {

    /** @return true if the provider accepted the message. */
    boolean send(String phoneE164, String title, String body, Map<String, Object> data);
}
