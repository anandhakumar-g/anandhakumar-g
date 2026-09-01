package com.singlepoint.provider;

import com.singlepoint.provider.domain.TenantServiceProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantServiceProviderRepository extends JpaRepository<TenantServiceProvider, UUID> {

    List<TenantServiceProvider> findByTenantIdAndActiveTrue(UUID tenantId);

    List<TenantServiceProvider> findByServiceProviderIdAndActiveTrue(UUID serviceProviderId);

    Optional<TenantServiceProvider> findByTenantIdAndServiceProviderId(UUID tenantId, UUID serviceProviderId);

    boolean existsByTenantIdAndServiceProviderId(UUID tenantId, UUID serviceProviderId);
}
