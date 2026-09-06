package com.singlepoint.notification.api;

import com.singlepoint.notification.NotificationPreferenceService;
import com.singlepoint.notification.domain.NotificationPreference;
import com.singlepoint.security.AppPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/me/notification-preferences")
@Tag(name = "Me — Notification preferences", description = "Anti-fatigue controls for promotional offers")
public class NotificationPreferenceController {

    private final NotificationPreferenceService service;

    public NotificationPreferenceController(NotificationPreferenceService service) {
        this.service = service;
    }

    public record PrefView(List<String> subscribedVendorCategoryIds, int promoFrequencyCapPerWeek,
                           String digestMode, boolean ticketNotificationsEnabled, boolean promoNotificationsEnabled,
                           boolean whatsappEnabled, boolean broadcastEnabled, boolean promoWhatsappEnabled) {
        static PrefView of(NotificationPreference p) {
            return new PrefView(List.copyOf(p.subscribedCategorySet()), p.getPromoFrequencyCapPerWeek(),
                    p.getDigestMode().name(), p.isTicketNotificationsEnabled(), p.isPromoNotificationsEnabled(),
                    p.isWhatsappEnabled(), p.isBroadcastEnabled(), p.isPromoWhatsappEnabled());
        }
    }

    public record UpdateRequest(List<String> subscribedVendorCategoryIds, Integer promoFrequencyCapPerWeek,
                                String digestMode, Boolean ticketNotificationsEnabled, Boolean promoNotificationsEnabled,
                                Boolean whatsappEnabled, Boolean broadcastEnabled, Boolean promoWhatsappEnabled) { }

    @GetMapping
    @Operation(summary = "Get my notification preferences (created with opt-in defaults on first read)")
    public ResponseEntity<PrefView> get(@AuthenticationPrincipal AppPrincipal p) {
        return ResponseEntity.ok(PrefView.of(service.getOrCreate(p.getUserId())));
    }

    @PutMapping
    @Operation(summary = "Update notification preferences")
    public ResponseEntity<PrefView> update(@AuthenticationPrincipal AppPrincipal p, @RequestBody UpdateRequest body) {
        NotificationPreference.DigestMode digest = body.digestMode() != null
                ? NotificationPreference.DigestMode.valueOf(body.digestMode().toUpperCase()) : null;
        var updated = service.update(p.getUserId(),
                body.subscribedVendorCategoryIds() != null ? new java.util.LinkedHashSet<>(body.subscribedVendorCategoryIds()) : null,
                body.promoFrequencyCapPerWeek(), digest,
                body.ticketNotificationsEnabled(), body.promoNotificationsEnabled(), body.whatsappEnabled(),
                body.broadcastEnabled(), body.promoWhatsappEnabled());
        return ResponseEntity.ok(PrefView.of(updated));
    }
}
