package com.singlepoint.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.singlepoint.notification.domain.DeviceToken;
import com.singlepoint.notification.domain.Notification;
import com.singlepoint.notification.domain.NotificationOutbox;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Turns one outbox row into per-recipient {@link Notification} rows + a push send. */
@Component
public class OutboxDispatcher {

    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationRepository notificationRepository;
    private final PushSender pushSender;
    private final ObjectMapper mapper;

    public OutboxDispatcher(DeviceTokenRepository deviceTokenRepository,
                            NotificationRepository notificationRepository,
                            PushSender pushSender, ObjectMapper mapper) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.notificationRepository = notificationRepository;
        this.pushSender = pushSender;
        this.mapper = mapper;
    }

    @Transactional
    public void dispatch(NotificationOutbox row) throws Exception {
        JsonNode payload = mapper.readTree(row.getPayload());
        String title = payload.path("title").asText("");
        String body = payload.path("body").asText("");
        Map<String, Object> data = mapper.convertValue(payload.path("data"), Map.class);
        if (data == null) data = Map.of();

        for (JsonNode r : payload.path("recipients")) {
            UUID userId = UUID.fromString(r.asText());
            List<String> tokens = new ArrayList<>();
            for (DeviceToken dt : deviceTokenRepository.findByUserId(userId)) {
                tokens.add(dt.getToken());
            }
            boolean ok = tokens.isEmpty() || pushSender.send(tokens, title, body, data);

            Notification n = new Notification();
            n.setTenantId(row.getTenantId());
            n.setUserId(userId);
            n.setChannel(Notification.Channel.PUSH);
            n.setTemplate(row.getEventType());
            n.setTitle(title);
            n.setBody(body);
            n.setData(mapper.writeValueAsString(data));
            if (tokens.isEmpty()) {
                n.setStatus(Notification.Status.SKIPPED);
                n.setError("no device tokens");
            } else if (ok) {
                n.setStatus(Notification.Status.SENT);
                n.setSentAt(Instant.now());
            } else {
                n.setStatus(Notification.Status.FAILED);
                n.setError("push transport rejected batch");
            }
            notificationRepository.save(n);
        }
    }
}
