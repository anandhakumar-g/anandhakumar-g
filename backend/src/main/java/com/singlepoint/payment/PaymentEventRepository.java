package com.singlepoint.payment;

import com.singlepoint.payment.domain.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, UUID> {

    List<PaymentEvent> findByTicketPaymentIdOrderByCreatedAtAsc(UUID ticketPaymentId);
}
