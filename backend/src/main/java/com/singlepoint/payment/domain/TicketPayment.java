package com.singlepoint.payment.domain;

import com.singlepoint.common.domain.BaseEntity;
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

/** A service charge on a resolved/closed ticket. Independent of the ticket state machine. */
@Entity
@Table(name = "ticket_payment")
@Getter
@Setter
public class TicketPayment extends BaseEntity {

    public enum Mode { CASH, ONLINE }

    public enum Status {
        PENDING, CASH_PENDING_OTP, PAID_ONLINE, PAID_CASH, WAIVED, FAILED;

        public boolean isPaid() { return this == PAID_ONLINE || this == PAID_CASH; }
        public boolean isSettled() { return isPaid() || this == WAIVED; }
    }

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", length = 8)
    private Mode mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "charged_by_user_id", nullable = false)
    private UUID chargedByUserId;

    @Column(name = "charged_by_role", nullable = false, length = 20)
    private String chargedByRole;

    @Column(name = "note")
    private String note;

    @Column(name = "gateway", length = 20)
    private String gateway;

    @Column(name = "gateway_ref", length = 120)
    private String gatewayRef;

    @Column(name = "gateway_payment_link")
    private String gatewayPaymentLink;

    @Column(name = "gateway_payment_id", length = 120)
    private String gatewayPaymentId;

    @Column(name = "otp_verified_at")
    private Instant otpVerifiedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "waived_by_user_id")
    private UUID waivedByUserId;

    @Column(name = "waived_reason")
    private String waivedReason;
}
