package com.singlepoint.notification.whatsapp;

import com.singlepoint.notification.WhatsAppSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * WhatsApp via the Meta (WhatsApp Business) Cloud API. Active only on the {@code cloud} profile.
 * Plain REST, no SDK. Live HTTP is the only part not exercised locally (same as
 * {@code RazorpayGateway}). Sends a pre-approved template with the title + body as body params.
 */
@Component
@Profile("cloud")
public class MetaCloudWhatsAppSender implements WhatsAppSender {

    private static final Logger log = LoggerFactory.getLogger(MetaCloudWhatsAppSender.class);

    private final RestTemplate rest = new RestTemplate();
    private final String token;
    private final String phoneNumberId;
    private final String template;

    public MetaCloudWhatsAppSender(@Value("${sp.whatsapp.meta.token}") String token,
                                   @Value("${sp.whatsapp.meta.phone-number-id}") String phoneNumberId,
                                   @Value("${sp.whatsapp.meta.template:sp_ticket_update}") String template) {
        this.token = token;
        this.phoneNumberId = phoneNumberId;
        this.template = template;
    }

    @Override
    public boolean send(String phoneE164, String title, String body, Map<String, Object> data) {
        String url = "https://graph.facebook.com/v20.0/" + phoneNumberId + "/messages";
        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", phoneE164.replace("+", ""),
                "type", "template",
                "template", Map.of(
                        "name", template,
                        "language", Map.of("code", "en"),
                        "components", List.of(Map.of("type", "body", "parameters", List.of(
                                Map.of("type", "text", "text", title),
                                Map.of("type", "text", "text", body))))));
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token);
        try {
            rest.postForEntity(url, new HttpEntity<>(payload, h), String.class);
            return true;
        } catch (Exception e) {
            log.warn("WhatsApp send failed: {}", e.getMessage());
            return false;
        }
    }
}
