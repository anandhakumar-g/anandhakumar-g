package com.singlepoint.provider.domain;

import com.singlepoint.common.domain.BaseEntity;
import com.singlepoint.crypto.EncryptedStringConverter;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A vendor / service provider in the global directory. May serve multiple tenants
 * (via {@link TenantServiceProvider}). Only VERIFIED + active providers are assignable.
 */
@Entity
@Table(name = "service_provider")
@Getter
@Setter
public class ServiceProvider extends BaseEntity {

    /** app_user (role PROVIDER) once the provider signs in; may be null before then. */
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "vendor_category_id", nullable = false)
    private UUID vendorCategoryId;

    @Column(name = "is_company", nullable = false)
    private boolean company = false;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "contact_phone_enc", nullable = false)
    private String contactPhone;

    @Column(name = "contact_phone_hash", nullable = false, length = 64)
    private String contactPhoneHash;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "contact_email_enc")
    private String contactEmail;

    @Column(name = "service_area", length = 200)
    private String serviceArea;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 24)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING_VERIFICATION;

    @Column(name = "verified_by_user_id")
    private UUID verifiedByUserId;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 20)
    private ProviderTier tier = ProviderTier.STANDARD;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability", nullable = false, length = 12)
    private Availability availability = Availability.AVAILABLE;

    @Column(name = "availability_note", length = 200)
    private String availabilityNote;

    /** Mean of resident ratings across this provider's closed tickets; null until the first rating. */
    @Column(name = "rating_avg", precision = 3, scale = 2)
    private java.math.BigDecimal ratingAvg;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount = 0;

    public boolean isAssignable() {
        return active && verificationStatus == VerificationStatus.VERIFIED;
    }
}
