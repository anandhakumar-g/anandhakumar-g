package com.singlepoint.offer;

import com.singlepoint.offer.domain.OfferFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfferFeedbackRepository extends JpaRepository<OfferFeedback, UUID> {

    Optional<OfferFeedback> findByOfferIdAndUserId(UUID offerId, UUID userId);

    List<OfferFeedback> findByOfferIdOrderByCreatedAtDesc(UUID offerId);

    @Query("select avg(f.rating), count(f.rating) from OfferFeedback f where f.offerId = :offerId")
    List<Object[]> ratingAggregate(UUID offerId);
}
