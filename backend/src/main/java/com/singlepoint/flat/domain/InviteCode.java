package com.singlepoint.flat.domain;

import com.singlepoint.common.domain.BaseEntity;
import com.singlepoint.user.domain.MembershipRelation;
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
@Table(name = "invite_code")
@Getter
@Setter
public class InviteCode extends BaseEntity {

    public enum Status { ACTIVE, REVOKED, EXHAUSTED, EXPIRED }

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "flat_id")
    private UUID flatId;

    @Column(name = "code", nullable = false, length = 16)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation", nullable = false, length = 20)
    private MembershipRelation relation = MembershipRelation.OCCUPANT;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "max_uses", nullable = false)
    private int maxUses = 1;

    @Column(name = "use_count", nullable = false)
    private int useCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    public boolean isRedeemable() {
        if (status != Status.ACTIVE) return false;
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) return false;
        return useCount < maxUses;
    }
}
