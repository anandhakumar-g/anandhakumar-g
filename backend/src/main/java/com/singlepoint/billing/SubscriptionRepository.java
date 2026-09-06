package com.singlepoint.billing;

import com.singlepoint.billing.domain.SubjectType;
import com.singlepoint.billing.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findBySubjectTypeAndSubjectIdAndStatusNot(
            SubjectType subjectType, UUID subjectId, Subscription.Status status);

    List<Subscription> findBySubjectType(SubjectType subjectType);

    List<Subscription> findByStatusInAndCurrentPeriodEndBefore(List<Subscription.Status> statuses, Instant cutoff);

    List<Subscription> findByStatusAndGraceUntilBefore(Subscription.Status status, Instant cutoff);

    List<Subscription> findByStatusAndGraceUntilAfter(Subscription.Status status, Instant cutoff);

    Optional<Subscription> findByGatewaySubscriptionId(String gatewaySubscriptionId);
}
