package com.singlepoint.notification;

import com.singlepoint.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Notification> findByUserIdAndChannelOrderByCreatedAtDesc(
            UUID userId, Notification.Channel channel, Pageable pageable);

    Page<Notification> findByUserIdAndChannelAndReadAtIsNullOrderByCreatedAtDesc(
            UUID userId, Notification.Channel channel, Pageable pageable);

    long countByUserIdAndChannelAndReadAtIsNull(UUID userId, Notification.Channel channel);

    java.util.Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
        "update Notification n set n.readAt = :at where n.userId = :userId and n.readAt is null")
    int markAllRead(UUID userId, java.time.Instant at);

    long countByUserIdAndTemplateStartingWithAndStatusAndCreatedAtAfter(
            UUID userId, String templatePrefix, Notification.Status status, Instant after);

    List<Notification> findByStatusAndTemplate(Notification.Status status, String template);
}
