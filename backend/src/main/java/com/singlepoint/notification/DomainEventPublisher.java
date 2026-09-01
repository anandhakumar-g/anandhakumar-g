package com.singlepoint.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.singlepoint.notification.domain.NotificationOutbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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

    public void publish(String eventType, String aggregateType, UUID aggregateId, UUID tenantId,
                        List<UUID> recipientUserIds, String title, String body, Map<String, Object> data) {
        try {
            Map<String, Object> payload = Map.of(
                    "recipients", recipientUserIds.stream().map(UUID::toString).collect(Collectors.toList()),
                    "title", title == null ? "" : title,
                    "body", body == null ? "" : body,
                    "data", data == null ? Map.of() : data);
            NotificationOutbox row = new NotificationOutbox();
            row.setEventType(eventType);
            row.setAggregateType(aggregateType);
            row.setAggregateId(aggregateId);
            row.setTenantId(tenantId);
            row.setPayload(mapper.writeValueAsString(payload));
            outboxRepository.save(row);
        } catch (Exception e) {
            // Never let a notification bookkeeping failure break the business transaction.
            log.error("Failed to enqueue outbox event {} for {}", eventType, aggregateId, e);
        }
    }
}
