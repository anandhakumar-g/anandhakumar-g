package com.singlepoint.payment.domain;

import com.singlepoint.common.domain.CreatedOnlyEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.UUID;

/** Append-only forensic trail for a payment (link created, webhook received, OTP verified, waived). */
@Entity
@Table(name = "payment_event")
@Getter
@Setter
public class PaymentEvent extends CreatedOnlyEntity {

    /** MVP-10 (C): null for a community-less direct booking's event. */
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "ticket_payment_id", nullable = false)
    private UUID ticketPaymentId;

    @Column(name = "type", nullable = false, length = 40)
    private String type;

    @Column(name = "detail")
    private String detail;

    public static PaymentEvent of(TicketPayment p, String type, String detail) {
        PaymentEvent e = new PaymentEvent();
        e.setTenantId(p.getTenantId());
        e.setTicketPaymentId(p.getId());
        e.setType(type);
        e.setDetail(detail);
        return e;
    }
}
