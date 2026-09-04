package com.singlepoint.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.singlepoint.billing.domain.SubjectType;
import com.singlepoint.entitlement.EntitlementService;
import com.singlepoint.notification.domain.DeviceToken;
import com.singlepoint.notification.domain.Notification;
import com.singlepoint.notification.domain.NotificationOutbox;
import com.singlepoint.notification.domain.NotificationPreference;
import com.singlepoint.user.AppUserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns one outbox row into per-recipient {@link Notification} rows + a push send, applying
 * the anti-fatigue gate for promotional events (blueprint 4.16):
 * per-user opt-out, per-category subscription, central weekly frequency cap, digest deferral.
 */
@Component
public class OutboxDispatcher {

    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final PushSender pushSender;
    private final WhatsAppSender whatsAppSender;
    private final EntitlementService entitlements;
    private final AppUserRepository userRepository;
    private final ObjectMapper mapper;

    public OutboxDispatcher(DeviceTokenRepository deviceTokenRepository,
                            NotificationRepository notificationRepository,
                            NotificationPreferenceRepository preferenceRepository,
                            PushSender pushSender, WhatsAppSender whatsAppSender,
                            EntitlementService entitlements, AppUserRepository userRepository,
                            ObjectMapper mapper) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.pushSender = pushSender;
        this.whatsAppSender = whatsAppSender;
        this.entitlements = entitlements;
        this.userRepository = userRepository;
        this.mapper = mapper;
    }

    @Transactional
    public void dispatch(NotificationOutbox row) throws Exception {
        JsonNode payload = mapper.readTree(row.getPayload());
        String title = payload.path("title").asText("");
        String body = payload.path("body").asText("");
        boolean promo = payload.path("promo").asBoolean(false);
        String kind = payload.path("kind").asText(promo ? "promo" : "transactional");
        String vendorCategoryId = payload.hasNonNull("vendorCategoryId")
                ? payload.get("vendorCategoryId").asText() : null;
        @SuppressWarnings("unchecked")
        Map<String, Object> data = mapper.convertValue(payload.path("data"), Map.class);
        if (data == null) data = Map.of();

        for (JsonNode r : payload.path("recipients")) {
            deliver(row, UUID.fromString(r.asText()), title, body, data, promo, kind, vendorCategoryId);
        }
    }

    private void deliver(NotificationOutbox row, UUID userId, String title, String body,
                         Map<String, Object> data, boolean promo, String kind, String vendorCategoryId) throws Exception {
        NotificationPreference pref = preferenceRepository.findByUserId(userId).orElse(null);

        String skipReason = gate(pref, promo, kind, vendorCategoryId, userId);
        if (skipReason != null) {
            record(row, userId, title, body, data, Notification.Status.SKIPPED, skipReason, null,
                    Notification.Channel.PUSH);
            return;
        }

        // Digest deferral: queue for OfferDigestJob rather than push now.
        if (promo && pref != null && pref.getDigestMode() != NotificationPreference.DigestMode.OFF) {
            record(row, userId, title, body, data, Notification.Status.QUEUED, "digest", "OFFER_DIGEST_PENDING",
                    Notification.Channel.PUSH);
            return;
        }

        List<String> tokens = new ArrayList<>();
        for (DeviceToken dt : deviceTokenRepository.findByUserId(userId)) tokens.add(dt.getToken());
        if (tokens.isEmpty()) {
            record(row, userId, title, body, data, Notification.Status.SKIPPED, "no device tokens", null,
                    Notification.Channel.PUSH);
        } else {
            boolean ok = pushSender.send(tokens, title, body, data);
            record(row, userId, title, body, data,
                    ok ? Notification.Status.SENT : Notification.Status.FAILED,
                    ok ? null : "push transport rejected batch", null, Notification.Channel.PUSH);
        }

        // Additive WhatsApp delivery for ticket notifications, when the community is entitled
        // and the user has opted in. Broadcasts are push + in-app only this MVP.
        if (!promo && !"broadcast".equals(kind)) maybeSendWhatsApp(row, userId, title, body, data, pref);
    }

    private void maybeSendWhatsApp(NotificationOutbox row, UUID userId, String title, String body,
                                   Map<String, Object> data, NotificationPreference pref) throws Exception {
        if (pref == null || !pref.isWhatsappEnabled()) return;
        if (row.getTenantId() == null
                || !entitlements.isEntitled(SubjectType.TENANT, row.getTenantId(), "WHATSAPP_NOTIFICATIONS")) {
            return;
        }
        String phone = userRepository.findById(userId).map(u -> u.getPhone()).orElse(null);
        if (phone == null || phone.isBlank()) return;
        boolean ok = whatsAppSender.send(phone, title, body, data);
        record(row, userId, title, body, data,
                ok ? Notification.Status.SENT : Notification.Status.FAILED,
                ok ? null : "whatsapp transport rejected", null, Notification.Channel.WHATSAPP);
    }

    /** @return skip reason, or null to proceed. */
    private String gate(NotificationPreference pref, boolean promo, String kind, String vendorCategoryId, UUID userId) {
        if ("broadcast".equals(kind)) {
            return (pref != null && !pref.isBroadcastEnabled()) ? "broadcasts muted" : null;
        }
        if (!promo) {
            return (pref != null && !pref.isTicketNotificationsEnabled()) ? "ticket notifications muted" : null;
        }
        if (pref == null) return "not subscribed to this category"; // opt-in default = no subscriptions
        if (!pref.isPromoNotificationsEnabled()) return "promotional notifications off";
        if (vendorCategoryId != null && !pref.subscribedCategorySet().contains(vendorCategoryId)) {
            return "not subscribed to this category";
        }
        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        long sentThisWeek = notificationRepository
                .countByUserIdAndTemplateStartingWithAndStatusAndCreatedAtAfter(
                        userId, "OFFER_", Notification.Status.SENT, weekAgo);
        if (sentThisWeek >= pref.getPromoFrequencyCapPerWeek()) return "weekly promo cap reached";
        return null;
    }

    private void record(NotificationOutbox row, UUID userId, String title, String body, Map<String, Object> data,
                        Notification.Status status, String note, String templateOverride,
                        Notification.Channel channel) throws Exception {
        Notification n = new Notification();
        n.setTenantId(row.getTenantId());
        n.setUserId(userId);
        n.setChannel(channel);
        n.setTemplate(templateOverride != null ? templateOverride : row.getEventType());
        n.setTitle(title);
        n.setBody(body);
        n.setData(mapper.writeValueAsString(data));
        n.setStatus(status);
        if (status == Notification.Status.SENT) n.setSentAt(Instant.now());
        if (note != null) n.setError(note);
        notificationRepository.save(n);
    }
}
