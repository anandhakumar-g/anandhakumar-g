package com.singlepoint.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.singlepoint.notification.domain.NotificationOutbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Writes a notification outbox row in the caller's transaction. The caller supplies the
 * fully-rendered recipient list + copy so the notification module stays decoupled from
 * ticket / user domains (see docs/decisions.md ADR-003).
 */
@Component
public class DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final NotificationOutboxRepository outboxRepository;
    private final ObjectMapper mapper;

    public DomainEventPublisher(NotificationOutboxRepository outboxRepository, ObjectMapper mapper) {
        this.outboxRepository = outboxRepository;
        this.mapper = mapper;
    }

    /** Transactional notification (ticket status etc.) — gated only by the ticket opt-out. */
    public void publish(String eventType, String aggregateType, UUID aggregateId, UUID tenantId,
                        List<UUID> recipientUserIds, String title, String body, Map<String, Object> data) {
        enqueue(eventType, aggregateType, aggregateId, tenantId, recipientUserIds, title, body, data, false, null);
    }

    /**
     * Promotional notification (offers) — the {@link OutboxDispatcher} applies the anti-fatigue
     * gate: per-user opt-out, per-category subscription, weekly frequency cap and digest mode.
     */
    public void publishPromo(String eventType, UUID offerId, UUID vendorCategoryId,
                             List<UUID> recipientUserIds, String title, String body, Map<String, Object> data) {
        enqueue(eventType, "offer", offerId, null, recipientUserIds, title, body, data, true,
                vendorCategoryId != null ? vendorCategoryId.toString() : null);
    }

    private void enqueue(String eventType, String aggregateType, UUID aggregateId, UUID tenantId,
                         List<UUID> recipientUserIds, String title, String body, Map<String, Object> data,
                         boolean promo, String vendorCategoryId) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("recipients", recipientUserIds.stream().map(UUID::toString).collect(Collectors.toList()));
            payload.put("title", title == null ? "" : title);
            payload.put("body", body == null ? "" : body);
            payload.put("data", data == null ? Map.of() : data);
            payload.put("promo", promo);
            if (vendorCategoryId != null) payload.put("vendorCategoryId", vendorCategoryId);

            NotificationOutbox row = new NotificationOutbox();
            row.setEventType(eventType);
            row.setAggregateType(aggregateType);
            row.setAggregateId(aggregateId);
            row.setTenantId(tenantId);
            row.setPayload(mapper.writeValueAsString(payload));
            outboxRepository.save(row);
        } catch (Exception e) {
            log.error("Failed to enqueue outbox event {} for {}", eventType, aggregateId, e);
        }
    }
}
