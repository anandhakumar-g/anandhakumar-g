package com.singlepoint.notification.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.singlepoint.notification.PushSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Sends via the Expo push service (https://exp.host). FCM can be added behind the same interface. */
@Component
@ConditionalOnProperty(name = "sp.push.provider", havingValue = "expo")
public class ExpoPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(ExpoPushSender.class);

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String endpoint;

    public ExpoPushSender(@Value("${sp.push.expo.endpoint:https://exp.host/--/api/v2/push/send}") String endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public boolean send(List<String> deviceTokens, String title, String body, Map<String, Object> data) {
        if (deviceTokens.isEmpty()) return true;
        List<Map<String, Object>> messages = new ArrayList<>();
        for (String token : deviceTokens) {
            messages.add(Map.of("to", token, "title", title, "body", body,
                    "data", data == null ? Map.of() : data, "sound", "default"));
        }
        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            var req = new org.springframework.http.HttpEntity<>(mapper.writeValueAsString(messages), headers);
            rest.postForEntity(endpoint, req, String.class);
            return true;
        } catch (Exception e) {
            log.warn("Expo push failed: {}", e.toString());
            return false;
        }
    }
}
