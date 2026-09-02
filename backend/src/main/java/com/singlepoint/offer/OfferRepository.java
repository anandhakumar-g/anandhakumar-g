package com.singlepoint.offer;

import com.singlepoint.offer.domain.Offer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OfferRepository extends JpaRepository<Offer, UUID> {

    Page<Offer> findByCreatedByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Offer> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Page<Offer> findByStatusOrderByCreatedAtDesc(Offer.Status status, Pageable pageable);

    List<Offer> findByStatusAndValidToBefore(Offer.Status status, Instant cutoff);
}
