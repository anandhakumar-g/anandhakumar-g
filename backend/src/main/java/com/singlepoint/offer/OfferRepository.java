package com.singlepoint.offer;

import com.singlepoint.offer.domain.Offer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import javax.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfferRepository extends JpaRepository<Offer, UUID> {

    /** MVP-8: serialise redemptions of one offer so the global cap can't be raced past. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Offer o where o.id = :id")
    Optional<Offer> findByIdForUpdate(UUID id);

    Page<Offer> findByCreatedByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Offer> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Page<Offer> findByStatusOrderByCreatedAtDesc(Offer.Status status, Pageable pageable);

    List<Offer> findByStatusAndValidToBefore(Offer.Status status, Instant cutoff);

    long countByServiceProviderIdAndCreatedAtAfter(UUID serviceProviderId, Instant after);

    long countByTenantIdAndCreatedAtAfter(UUID tenantId, Instant after);

    /** MVP-10 (A): Super Admin dashboard — offers awaiting approval. */
    long countByStatus(Offer.Status status);
}
