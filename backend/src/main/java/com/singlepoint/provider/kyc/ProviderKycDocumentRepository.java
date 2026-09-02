package com.singlepoint.provider.kyc;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProviderKycDocumentRepository extends JpaRepository<ProviderKycDocument, UUID> {

    List<ProviderKycDocument> findByServiceProviderIdOrderByCreatedAtAsc(UUID serviceProviderId);

    List<ProviderKycDocument> findByServiceProviderIdAndStatus(UUID serviceProviderId, ProviderKycDocument.Status status);
}
