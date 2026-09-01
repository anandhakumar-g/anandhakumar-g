package com.singlepoint.notification;

import com.singlepoint.notification.domain.NotificationOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {

    List<NotificationOutbox> findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            NotificationOutbox.Status status, Instant now, Pageable pageable);

    long countByStatus(NotificationOutbox.Status status);
}
