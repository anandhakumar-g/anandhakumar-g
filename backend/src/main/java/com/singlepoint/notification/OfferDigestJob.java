package com.singlepoint.notification;

import com.singlepoint.notification.domain.DeviceToken;
import com.singlepoint.notification.domain.Notification;
import com.singlepoint.notification.domain.NotificationPreference;
import com.singlepoint.security.TenantScopedExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bundles deferred promotional notifications ({@code OFFER_DIGEST_PENDING}) into a single
 * "deals" push per user. MVP-2 runs one pass on a configurable cron for everyone whose
 * digest mode is not OFF; DAILY vs WEEKLY cadence tuning is a later refinement.
 */
@Component
public class OfferDigestJob {

    private static final Logger log = LoggerFactory.getLogger(OfferDigestJob.class);
    private static final String PENDING = "OFFER_DIGEST_PENDING";

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final PushSender pushSender;
    private final TenantScopedExecutor tenantScoped;

    public OfferDigestJob(NotificationRepository notificationRepository,
                          NotificationPreferenceRepository preferenceRepository,
                          DeviceTokenRepository deviceTokenRepository,
                          PushSender pushSender, TenantScopedExecutor tenantScoped) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.pushSender = pushSender;
        this.tenantScoped = tenantScoped;
    }

    @Scheduled(cron = "${sp.notification.digest.cron:0 30 8 * * *}")
    public void run() {
        tenantScoped.inWildcard(this::process);
    }

    @Transactional
    public void process() {
        List<Notification> pending =
                notificationRepository.findByStatusAndTemplate(Notification.Status.QUEUED, PENDING);
        if (pending.isEmpty()) return;

        Map<UUID, List<Notification>> byUser = new LinkedHashMap<>();
        for (Notification n : pending) byUser.computeIfAbsent(n.getUserId(), k -> new ArrayList<>()).add(n);

        int digestsSent = 0;
        for (Map.Entry<UUID, List<Notification>> e : byUser.entrySet()) {
            UUID userId = e.getKey();
            NotificationPreference pref = preferenceRepository.findByUserId(userId).orElse(null);
            if (pref == null || pref.getDigestMode() == NotificationPreference.DigestMode.OFF) {
                continue; // preference changed since queueing — leave the rows, they'll be pruned later
            }
            List<Notification> items = e.getValue();
            String title = items.size() == 1 ? "A new deal for you" : items.size() + " new deals for you";
            String body = items.stream().map(Notification::getTitle).limit(3)
                    .reduce((a, b) -> a + " · " + b).orElse("Open the Deals tab to see what's new");

            List<String> tokens = new ArrayList<>();
            for (DeviceToken dt : deviceTokenRepository.findByUserId(userId)) tokens.add(dt.getToken());
            boolean ok = tokens.isEmpty() || pushSender.send(tokens, title, body, Map.of("type", "offer_digest"));

            Notification digest = new Notification();
            digest.setUserId(userId);
            digest.setChannel(Notification.Channel.PUSH);
            digest.setTemplate("OFFER_DIGEST");
            digest.setTitle(title);
            digest.setBody(body);
            digest.setStatus(ok && !tokens.isEmpty() ? Notification.Status.SENT
                    : tokens.isEmpty() ? Notification.Status.SKIPPED : Notification.Status.FAILED);
            if (digest.getStatus() == Notification.Status.SENT) digest.setSentAt(Instant.now());
            notificationRepository.save(digest);

            for (Notification n : items) {
                n.setStatus(Notification.Status.SENT);
                n.setSentAt(Instant.now());
                n.setError("bundled into digest");
            }
            notificationRepository.saveAll(items);
            digestsSent++;
        }
        if (digestsSent > 0) log.info("Sent {} offer digest(s)", digestsSent);
    }
}
