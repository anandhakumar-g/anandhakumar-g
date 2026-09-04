package com.singlepoint.offer.domain;

import com.singlepoint.common.domain.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Audience for one offer. Set by the author, then reviewed / overridden by the Super Admin at
 * approval time — the deliberate control point against mistargeted or excessive offers.
 */
@Entity
@Table(name = "offer_target")
@Getter
@Setter
public class OfferTarget extends BaseEntity {

    public enum TargetType { SINGLE_TENANT, TENANT_LIST, ALL_TENANTS, USER_SEGMENT, ENQUIRY_BASED, USER_LIST }

    @Column(name = "offer_id", nullable = false)
    private UUID offerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 16)
    private TargetType targetType;

    /** CSV of tenant UUIDs for SINGLE_TENANT / TENANT_LIST. */
    @Column(name = "tenant_ids")
    private String tenantIds;

    /** MVP-8: CSV of app_user UUIDs for USER_LIST (resolved from phone numbers). */
    @Column(name = "user_ids")
    private String userIds;

    /** JSON blob for USER_SEGMENT, e.g. {"block":"A"} or {"usedVendorCategoryId":"..."}. */
    @Column(name = "segment_filter")
    private String segmentFilter;

    @Column(name = "enquiry_category_id")
    private UUID enquiryCategoryId;

    @Column(name = "set_by_user_id")
    private UUID setByUserId;

    public List<UUID> tenantIdList() {
        return csvToUuids(tenantIds);
    }

    public List<UUID> userIdList() {
        return csvToUuids(userIds);
    }

    private static List<UUID> csvToUuids(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(UUID::fromString).toList();
    }
}
