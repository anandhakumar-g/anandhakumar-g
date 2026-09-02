package com.singlepoint.offer;

import com.singlepoint.offer.domain.OfferRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OfferRedemptionRepository extends JpaRepository<OfferRedemption, UUID> {

    long countByOfferIdAndStatus(UUID offerId, OfferRedemption.Status status);

    long countByOfferIdAndUserIdAndStatus(UUID offerId, UUID userId, OfferRedemption.Status status);

    List<OfferRedemption> findByUserIdOrderByRedeemedAtDesc(UUID userId);
}
