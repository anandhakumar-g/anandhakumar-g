package com.singlepoint.user.domain;

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
import java.util.UUID;

/**
 * A person (or company account). Global — identity is the phone number, not a tenant.
 * {@code phone} / {@code email} are encrypted at rest; {@code phoneHash} / {@code emailHash}
 * are keyed HMACs used for unique constraints and lookup.
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
public class AppUser extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 20)
    private UserType userType = UserType.INDIVIDUAL;

    @Column(name = "name", length = 160)
    private String name;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "phone_enc", nullable = false)
    private String phone;

    @Column(name = "phone_hash", nullable = false, length = 64)
    private String phoneHash;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "email_enc")
    private String email;

    @Column(name = "email_hash", length = 64)
    private String emailHash;

    @Column(name = "preferred_theme", length = 40)
    private String preferredTheme;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 20)
    private KycStatus kycStatus = KycStatus.NOT_REQUIRED;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "current_tenant_id")
    private UUID currentTenantId;

    @Column(name = "profile_completed", nullable = false)
    private boolean profileCompleted = false;

    /** Self-reported: the user is away until this instant. Informational only. */
    @Column(name = "away_until")
    private java.time.Instant awayUntil;

    /** MVP-13 (C2): set when the user closes their account — PII is scrubbed, tokens are rejected. */
    @Column(name = "deleted_at")
    private java.time.Instant deletedAt;
}
