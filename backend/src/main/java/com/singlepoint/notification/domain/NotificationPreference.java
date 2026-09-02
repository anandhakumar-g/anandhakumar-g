package com.singlepoint.notification.domain;

import com.singlepoint.common.domain.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Per-user notification controls (blueprint 4.16). Default = opt-in: no subscribed vendor
 * categories, so promotional pushes are suppressed until the resident actively subscribes;
 * ticket notifications are on and controlled independently.
 */
@Entity
@Table(name = "notification_preference")
@Getter
@Setter
public class NotificationPreference extends BaseEntity {

    public enum DigestMode { OFF, DAILY, WEEKLY }

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** CSV of vendor_category UUIDs the user wants offers from. */
    @Column(name = "subscribed_vendor_category_ids", nullable = false)
    private String subscribedVendorCategoryIds = "";

    @Column(name = "promo_frequency_cap_per_week", nullable = false)
    private int promoFrequencyCapPerWeek = 5;

    @Enumerated(EnumType.STRING)
    @Column(name = "digest_mode", nullable = false, length = 8)
    private DigestMode digestMode = DigestMode.OFF;

    @Column(name = "ticket_notifications_enabled", nullable = false)
    private boolean ticketNotificationsEnabled = true;

    @Column(name = "promo_notifications_enabled", nullable = false)
    private boolean promoNotificationsEnabled = true;

    public Set<String> subscribedCategorySet() {
        if (subscribedVendorCategoryIds == null || subscribedVendorCategoryIds.isBlank()) return Set.of();
        return Arrays.stream(subscribedVendorCategoryIds.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void setSubscribedCategorySet(Set<String> ids) {
        this.subscribedVendorCategoryIds = ids == null ? "" : String.join(",", ids);
    }
}
