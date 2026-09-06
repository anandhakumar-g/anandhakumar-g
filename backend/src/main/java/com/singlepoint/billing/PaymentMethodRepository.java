package com.singlepoint.billing;

import com.singlepoint.billing.domain.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {

    List<PaymentMethod> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, PaymentMethod.Status status);

    Optional<PaymentMethod> findFirstByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, PaymentMethod.Status status);
}
