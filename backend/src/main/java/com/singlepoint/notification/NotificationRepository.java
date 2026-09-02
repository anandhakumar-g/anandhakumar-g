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

    long countByUserIdAndTemplateStartingWithAndStatusAndCreatedAtAfter(
            UUID userId, String templatePrefix, Notification.Status status, Instant after);

    List<Notification> findByStatusAndTemplate(Notification.Status status, String template);
}
