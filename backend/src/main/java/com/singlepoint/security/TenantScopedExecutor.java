package com.singlepoint.security;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Runs a unit of work under an explicit tenant scope, applying {@code app.current_tenant_id}
 * to the DB session. Use for flows where the request thread has no tenant context yet
 * (first community join), for cross-tenant Super Admin work, and for system jobs.
 */
@Component
public class TenantScopedExecutor {

    private final TransactionTemplate txTemplate;

    public TenantScopedExecutor(PlatformTransactionManager transactionManager) {
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    public <T> T inTenant(UUID tenantId, Supplier<T> work) {
        return run(tenantId != null ? tenantId.toString() : null, work);
    }

    public void inTenant(UUID tenantId, Runnable work) {
        inTenant(tenantId, () -> { work.run(); return null; });
    }

    public <T> T inWildcard(Supplier<T> work) {
        return run(TenantContext.WILDCARD, work);
    }

    public void inWildcard(Runnable work) {
        inWildcard(() -> { work.run(); return null; });
    }

    private <T> T run(String scope, Supplier<T> work) {
        String previous = TenantContext.get();
        if (TenantContext.WILDCARD.equals(scope)) {
            TenantContext.setWildcard();
        } else if (scope != null) {
            TenantContext.setTenant(UUID.fromString(scope));
        } else {
            TenantContext.clear();
        }
        try {
            return txTemplate.execute(status -> work.get());
        } finally {
            if (previous == null) {
                TenantContext.clear();
            } else if (TenantContext.WILDCARD.equals(previous)) {
                TenantContext.setWildcard();
            } else {
                TenantContext.setTenant(UUID.fromString(previous));
            }
        }
    }
}
