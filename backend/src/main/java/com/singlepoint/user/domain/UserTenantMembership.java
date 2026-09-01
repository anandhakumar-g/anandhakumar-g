package com.singlepoint.user.domain;

import com.singlepoint.common.domain.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A user's membership of a tenant (community), scoped to a flat. Tenant-scoped table (RLS). */
@Entity
@Table(name = "user_tenant_membership")
@Getter
@Setter
public class UserTenantMembership extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "flat_id")
    private UUID flatId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation", nullable = false, length = 20)
    private MembershipRelation relation = MembershipRelation.OCCUPANT;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MembershipStatus status = MembershipStatus.PENDING_APPROVAL;

    @Column(name = "requested_flat_label", length = 120)
    private String requestedFlatLabel;

    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;

    @Column(name = "joined_at")
    private Instant joinedAt;

    @Column(name = "exited_at")
    private Instant exitedAt;
}
