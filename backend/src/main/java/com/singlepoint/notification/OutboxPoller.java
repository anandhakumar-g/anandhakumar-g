package com.singlepoint.notification;

import com.singlepoint.notification.domain.NotificationOutbox;
import com.singlepoint.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Polls the outbox and delivers due notifications with exponential backoff. */
@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final NotificationOutboxRepository repository;
    private final OutboxDispatcher dispatcher;
    private final int batchSize;
    private final int maxAttempts;

    public OutboxPoller(NotificationOutboxRepository repository, OutboxDispatcher dispatcher,
                        @Value("${sp.notification.outbox.batch-size:50}") int batchSize,
                        @Value("${sp.notification.outbox.max-attempts:6}") int maxAttempts) {
        this.repository = repository;
        this.dispatcher = dispatcher;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${sp.notification.outbox.poll-delay-ms:5000}")
    public void poll() {
        // Outbox + notification + device_token tables are not RLS-scoped; use wildcard so any
        // incidental tenant-scoped read is permitted for this system job.
        TenantContext.setWildcard();
        try {
            List<NotificationOutbox> due = repository
                    .findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                            NotificationOutbox.Status.PENDING, Instant.now(), PageRequest.of(0, batchSize));
            for (NotificationOutbox row : due) {
                processOne(row.getId());
            }
        } catch (Exception e) {
            log.error("Outbox poll failed", e);
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional
    public void processOne(java.util.UUID id) {
        NotificationOutbox row = repository.findById(id).orElse(null);
        if (row == null || row.getStatus() != NotificationOutbox.Status.PENDING) return;
        try {
            dispatcher.dispatch(row);
            row.setStatus(NotificationOutbox.Status.SENT);
            row.setProcessedAt(Instant.now());
        } catch (Exception e) {
            row.setAttempts(row.getAttempts() + 1);
            row.setLastError(String.valueOf(e.getMessage()));
            if (row.getAttempts() >= maxAttempts) {
                row.setStatus(NotificationOutbox.Status.DEAD);
                log.error("Outbox row {} exhausted retries", id, e);
            } else {
                long backoff = (long) Math.pow(2, row.getAttempts());
                row.setNextAttemptAt(Instant.now().plus(Duration.ofMinutes(backoff)));
                log.warn("Outbox row {} attempt {} failed, retrying in {}m", id, row.getAttempts(), backoff);
            }
        }
        repository.save(row);
    }
}
