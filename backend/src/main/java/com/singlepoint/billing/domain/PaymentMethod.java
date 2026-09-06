package com.singlepoint.billing.domain;

import com.singlepoint.common.domain.CreatedOnlyEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import java.util.UUID;

/** MVP-13 (A2): a saved gateway payment token for one user, used to auto-charge subscriptions. */
@Entity
@Table(name = "payment_method")
@Getter
@Setter
public class PaymentMethod extends CreatedOnlyEntity {

    public enum Status { ACTIVE, REMOVED }

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "gateway", nullable = false, length = 20)
    private String gateway;

    @Column(name = "gateway_customer_id", length = 80)
    private String gatewayCustomerId;

    @Column(name = "gateway_token", nullable = false, length = 120)
    private String gatewayToken;

    @Column(name = "brand", length = 20)
    private String brand;

    @Column(name = "last4", length = 4)
    private String last4;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private Status status = Status.ACTIVE;
}
