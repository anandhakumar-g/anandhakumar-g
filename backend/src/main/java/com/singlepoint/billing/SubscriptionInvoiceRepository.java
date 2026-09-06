package com.singlepoint.billing;

import com.singlepoint.billing.domain.SubjectType;
import com.singlepoint.billing.domain.SubscriptionInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionInvoiceRepository extends JpaRepository<SubscriptionInvoice, UUID> {

    Optional<SubscriptionInvoice> findByGatewayRef(String gatewayRef);

    Optional<SubscriptionInvoice> findFirstBySubscriptionIdAndStatusOrderByPeriodStartAsc(
            UUID subscriptionId, SubscriptionInvoice.Status status);

    List<SubscriptionInvoice> findBySubjectTypeAndSubjectIdOrderByCreatedAtDesc(SubjectType subjectType, UUID subjectId);

    List<SubscriptionInvoice> findBySubjectTypeAndSubjectIdAndStatus(
            SubjectType subjectType, UUID subjectId, SubscriptionInvoice.Status status);

    List<SubscriptionInvoice> findByStatusOrderByCreatedAtDesc(SubscriptionInvoice.Status status);
}
