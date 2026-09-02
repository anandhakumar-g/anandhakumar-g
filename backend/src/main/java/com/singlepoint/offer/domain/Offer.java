package com.singlepoint.offer.domain;

import com.singlepoint.common.domain.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A time-bound deal published by a verified vendor or a community admin. Every offer is
 * validated by a Super Admin before it goes ACTIVE (blueprint 4.16).
 */
@Entity
@Table(name = "offer")
@Getter
@Setter
public class Offer extends BaseEntity {

    public enum DiscountType { FLAT, PERCENTAGE }
    public enum Status { DRAFT, PENDING_APPROVAL, ACTIVE, EXPIRED, CANCELLED, REJECTED }

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "created_by_role", nullable = false, length = 20)
    private String createdByRole;

    /** Set when a vendor authored the offer. */
    @Column(name = "service_provider_id")
    private UUID serviceProviderId;

    /** Set when a community admin authored the offer. */
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "vendor_category_id", nullable = false)
    private UUID vendorCategoryId;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "image_key")
    private String imageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 12)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "coupon_code", length = 40)
    private String couponCode;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to", nullable = false)
    private Instant validTo;

    @Column(name = "redemption_limit_per_user", nullable = false)
    private int redemptionLimitPerUser = 1;

    @Column(name = "redemption_limit_total")
    private Integer redemptionLimitTotal;

    @Column(name = "terms")
    private String terms;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "validated_by_user_id")
    private UUID validatedByUserId;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "reject_reason")
    private String rejectReason;

    public boolean isLive(Instant now) {
        return status == Status.ACTIVE && !now.isBefore(validFrom) && now.isBefore(validTo);
    }
}
