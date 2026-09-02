package com.singlepoint.payment;

import com.singlepoint.payment.domain.TicketPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketPaymentRepository extends JpaRepository<TicketPayment, UUID> {

    List<TicketPayment> findByTicketId(UUID ticketId);

    Optional<TicketPayment> findByGatewayRef(String gatewayRef);
}
