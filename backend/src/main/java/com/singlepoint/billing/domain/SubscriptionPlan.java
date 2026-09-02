package com.singlepoint.billing.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.singlepoint.common.domain.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.util.Map;

@Entity
@Table(name = "subscription_plan")
@Getter
@Setter
public class SubscriptionPlan extends BaseEntity {

    public enum BillingCycle { MONTHLY, ANNUAL }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Enumerated(EnumType.STRING)
    @Column(name = "target", nullable = false, length = 10)
    private SubjectType target;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 8)
    private BillingCycle billingCycle = BillingCycle.MONTHLY;

    @Column(name = "price_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceAmount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    /** JSON map {FEATURE: limit}; -1 unlimited, 0 disabled, positive = per-month cap. */
    @Column(name = "entitlements", nullable = false)
    private String entitlements = "{}";

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "is_default", nullable = false)
    private boolean defaultPlan = false;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    public boolean isFree() {
        return priceAmount == null || priceAmount.signum() == 0;
    }

    public long limitFor(String feature) {
        try {
            Map<?, ?> m = MAPPER.readValue(entitlements, Map.class);
            Object v = m.get(feature);
            if (v == null) return -1;                    // absent => unlimited
            return ((Number) v).longValue();
        } catch (Exception e) {
            return -1;
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Long> entitlementMap() {
        try {
            Map<String, Object> m = MAPPER.readValue(entitlements, Map.class);
            java.util.LinkedHashMap<String, Long> out = new java.util.LinkedHashMap<>();
            m.forEach((k, v) -> out.put(k, ((Number) v).longValue()));
            return out;
        } catch (Exception e) {
            return Map.of();
        }
    }
}
