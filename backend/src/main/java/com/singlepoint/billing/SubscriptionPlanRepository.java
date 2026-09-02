package com.singlepoint.billing;

import com.singlepoint.billing.domain.SubjectType;
import com.singlepoint.billing.domain.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {

    Optional<SubscriptionPlan> findByCode(String code);

    Optional<SubscriptionPlan> findByTargetAndDefaultPlanTrue(SubjectType target);

    List<SubscriptionPlan> findByActiveTrueOrderByTargetAscSortOrderAsc();

    List<SubscriptionPlan> findAllByOrderByTargetAscSortOrderAsc();
}
