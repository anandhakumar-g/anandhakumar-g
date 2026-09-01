package com.singlepoint.provider.domain;

import com.singlepoint.common.domain.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.UUID;

/** Enrolment of a provider into a specific tenant's assignable directory. Tenant-scoped (RLS). */
@Entity
@Table(name = "tenant_service_provider")
@Getter
@Setter
public class TenantServiceProvider extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "service_provider_id", nullable = false)
    private UUID serviceProviderId;

    /** CSV of category UUIDs this provider covers for the tenant (unused in MVP-1 routing). */
    @Column(name = "category_ids")
    private String categoryIds;

    @Column(name = "service_area", length = 200)
    private String serviceArea;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
