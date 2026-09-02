package com.singlepoint.billing.domain;

import com.singlepoint.common.domain.CreatedOnlyEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscription_invoice")
@Getter
@Setter
public class SubscriptionInvoice extends CreatedOnlyEntity {

    public enum Status { DUE, PAID, VOID }

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 10)
    private SubjectType subjectType;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 8)
    private Status status = Status.DUE;

    @Column(name = "gateway", length = 20)
    private String gateway;

    @Column(name = "gateway_ref", length = 120)
    private String gatewayRef;

    @Column(name = "gateway_payment_link")
    private String gatewayPaymentLink;

    @Column(name = "paid_at")
    private Instant paidAt;
}
