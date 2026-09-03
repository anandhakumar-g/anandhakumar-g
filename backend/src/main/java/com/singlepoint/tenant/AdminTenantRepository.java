package com.singlepoint.tenant;

import com.singlepoint.tenant.domain.AdminTenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminTenantRepository extends JpaRepository<AdminTenant, UUID> {

    List<AdminTenant> findByAdminUserIdAndActiveTrueOrderByCreatedAtAsc(UUID adminUserId);

    Optional<AdminTenant> findByAdminUserIdAndTenantIdAndActiveTrue(UUID adminUserId, UUID tenantId);

    Optional<AdminTenant> findByAdminUserIdAndTenantId(UUID adminUserId, UUID tenantId);

    List<AdminTenant> findByTenantIdAndActiveTrue(UUID tenantId);

    long countByTenantIdAndActiveTrue(UUID tenantId);
}
