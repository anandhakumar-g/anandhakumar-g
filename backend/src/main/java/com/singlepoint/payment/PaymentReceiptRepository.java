package com.singlepoint.payment;

import com.singlepoint.payment.domain.PaymentReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, UUID> {

    Optional<PaymentReceipt> findByTicketPaymentId(UUID ticketPaymentId);

    @Query(value = "select nextval('receipt_seq')", nativeQuery = true)
    long nextReceiptSeq();
}
