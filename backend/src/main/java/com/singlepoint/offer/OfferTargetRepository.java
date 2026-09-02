package com.singlepoint.offer;

import com.singlepoint.offer.domain.OfferTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OfferTargetRepository extends JpaRepository<OfferTarget, UUID> {

    Optional<OfferTarget> findByOfferId(UUID offerId);
}
