package com.singlepoint.payment.domain;

import com.singlepoint.common.domain.CreatedOnlyEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Immutable digital receipt for a completed payment (DB trigger blocks UPDATE/DELETE). */
@Entity
@Table(name = "payment_receipt")
@Getter
@Setter
public class PaymentReceipt extends CreatedOnlyEntity {

    /** MVP-10 (C): null for a community-less direct booking's receipt. */
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "ticket_payment_id", nullable = false)
    private UUID ticketPaymentId;

    @Column(name = "receipt_number", nullable = false, length = 20)
    private String receiptNumber;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "mode", nullable = false, length = 8)
    private String mode;

    @Column(name = "ticket_reference", nullable = false, length = 20)
    private String ticketReference;

    @Column(name = "payer_name", length = 160)
    private String payerName;

    @Column(name = "payee_name", length = 160)
    private String payeeName;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();
}
