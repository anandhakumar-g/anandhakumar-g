package com.singlepoint.tenant;

import com.singlepoint.tenant.domain.AdminTenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminTenantRepository extends JpaRepository<AdminTenant, UUID> {

    /** MVP-9: every active admin, deduplicated across communities (broadcast fan-out). */
    @Query("select distinct a.adminUserId from AdminTenant a where a.active = true")
    List<UUID> findActiveAdminUserIds();

    List<AdminTenant> findByAdminUserIdAndActiveTrueOrderByCreatedAtAsc(UUID adminUserId);

    Optional<AdminTenant> findByAdminUserIdAndTenantIdAndActiveTrue(UUID adminUserId, UUID tenantId);

    Optional<AdminTenant> findByAdminUserIdAndTenantId(UUID adminUserId, UUID tenantId);

    List<AdminTenant> findByTenantIdAndActiveTrue(UUID tenantId);

    long countByTenantIdAndActiveTrue(UUID tenantId);
}
