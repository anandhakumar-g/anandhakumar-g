package com.singlepoint.provider;

import com.singlepoint.provider.domain.ServiceProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ServiceProviderRepository extends JpaRepository<ServiceProvider, UUID> {

    Optional<ServiceProvider> findByContactPhoneHash(String contactPhoneHash);

    Optional<ServiceProvider> findByUserId(UUID userId);
}
