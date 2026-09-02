package com.singlepoint.offer.domain;

import com.singlepoint.common.domain.CreatedOnlyEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "offer_redemption")
@Getter
@Setter
public class OfferRedemption extends CreatedOnlyEntity {

    public enum VerifiedBy { RESIDENT, PROVIDER, ADMIN }
    public enum Status { REDEEMED, VOID }

    @Column(name = "offer_id", nullable = false)
    private UUID offerId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "code_entered", length = 40)
    private String codeEntered;

    @Enumerated(EnumType.STRING)
    @Column(name = "verified_by", nullable = false, length = 12)
    private VerifiedBy verifiedBy = VerifiedBy.RESIDENT;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status = Status.REDEEMED;

    @Column(name = "redeemed_at", nullable = false)
    private Instant redeemedAt = Instant.now();

    @Column(name = "confirmed_at")
    private Instant confirmedAt;
}
