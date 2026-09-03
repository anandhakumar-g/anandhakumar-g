package com.singlepoint.tenant.domain;

import com.singlepoint.common.domain.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.UUID;

/**
 * MVP-7: maps an ADMIN {@code app_user} to a community it administers. An admin may
 * hold several of these; the one active tenant on the JWT is switched with the same
 * machinery residents use ({@code POST /me/active-community}).
 */
@Entity
@Table(name = "admin_tenant")
@Getter
@Setter
public class AdminTenant extends BaseEntity {

    @Column(name = "admin_user_id", nullable = false)
    private UUID adminUserId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "added_by_user_id")
    private UUID addedByUserId;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
