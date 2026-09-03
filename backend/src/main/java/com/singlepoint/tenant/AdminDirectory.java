package com.singlepoint.tenant;

import com.singlepoint.tenant.domain.AdminTenant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * MVP-7: the admins of a community, resolved from {@code admin_tenant} rather than
 * {@code app_user.current_tenant_id} (which only points at an admin's *active* community).
 * Used for seat counting, admin notifications and billing recipients.
 */
@Component
public class AdminDirectory {

    private final AdminTenantRepository adminTenants;

    public AdminDirectory(AdminTenantRepository adminTenants) {
        this.adminTenants = adminTenants;
    }

    @Transactional(readOnly = true)
    public List<UUID> adminUserIds(UUID tenantId) {
        return adminTenants.findByTenantIdAndActiveTrue(tenantId).stream()
                .map(AdminTenant::getAdminUserId).toList();
    }

    @Transactional(readOnly = true)
    public long seatCount(UUID tenantId) {
        return adminTenants.countByTenantIdAndActiveTrue(tenantId);
    }
}
