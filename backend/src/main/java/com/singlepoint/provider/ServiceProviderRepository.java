package com.singlepoint.provider;

import com.singlepoint.provider.domain.ServiceProvider;
import com.singlepoint.provider.domain.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ServiceProviderRepository extends JpaRepository<ServiceProvider, UUID> {

    Optional<ServiceProvider> findByContactPhoneHash(String contactPhoneHash);

    Optional<ServiceProvider> findByUserId(UUID userId);

    /** MVP-10 (A): Super Admin dashboard — providers awaiting verification. */
    long countByVerificationStatus(VerificationStatus status);
}
