package com.singlepoint.billing.api;

import com.singlepoint.billing.domain.SubjectType;
import com.singlepoint.billing.domain.Subscription;
import com.singlepoint.billing.domain.SubscriptionInvoice;
import com.singlepoint.billing.domain.SubscriptionPlan;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request / response shapes for the billing endpoints. */
public final class BillingDtos {

    private BillingDtos() { }

    // ---- views --------------------------------------------------------

    public record PlanView(UUID id, String target, String code, String name, String description,
                           String billingCycle, BigDecimal price, String currency,
                           Map<String, Long> entitlements, boolean active, boolean isDefault, int sortOrder) {
        public static PlanView of(SubscriptionPlan p) {
            return new PlanView(p.getId(), p.getTarget().name(), p.getCode(), p.getName(), p.getDescription(),
                    p.getBillingCycle().name(), p.getPriceAmount(), p.getCurrency(), p.entitlementMap(),
                    p.isActive(), p.isDefaultPlan(), p.getSortOrder());
        }
    }

    public record SubscriptionView(UUID id, String subjectType, UUID subjectId, UUID planId, String planCode,
                                   String planName, String status, Instant currentPeriodStart,
                                   Instant currentPeriodEnd, Instant graceUntil, boolean autoRenew,
                                   String gatewaySubscriptionId, String pendingMandateUrl) {
        public static SubscriptionView of(Subscription s, SubscriptionPlan plan) {
            return new SubscriptionView(s.getId(), s.getSubjectType().name(), s.getSubjectId(), s.getPlanId(),
                    plan != null ? plan.getCode() : null, plan != null ? plan.getName() : null,
                    s.getStatus().name(), s.getCurrentPeriodStart(), s.getCurrentPeriodEnd(),
                    s.getGraceUntil(), s.isAutoRenew(), s.getGatewaySubscriptionId(), s.getPendingMandateUrl());
        }
    }

    public record InvoiceView(UUID id, UUID subscriptionId, String subjectType, UUID subjectId,
                              BigDecimal amount, String currency, Instant periodStart, Instant periodEnd,
                              String status, String paymentLink, Instant paidAt, Instant createdAt) {
        public static InvoiceView of(SubscriptionInvoice i) {
            return new InvoiceView(i.getId(), i.getSubscriptionId(), i.getSubjectType().name(), i.getSubjectId(),
                    i.getAmount(), i.getCurrency(), i.getPeriodStart(), i.getPeriodEnd(), i.getStatus().name(),
                    i.getGatewayPaymentLink(), i.getPaidAt(), i.getCreatedAt());
        }
    }

    public record UsageView(String feature, long limit, long used) { }

    public record MyBillingView(String subjectType, UUID subjectId, String providerTier, PlanView plan,
                                SubscriptionView subscription, List<UsageView> usage,
                                List<InvoiceView> dueInvoices, List<PlanView> upgradeOptions,
                                PaymentMethodView savedPaymentMethod, boolean recurring, Instant nextChargeAt) { }

    public record PaymentMethodView(UUID id, String gateway, String brand, String last4, String status,
                                    Instant createdAt) {
        public static PaymentMethodView of(com.singlepoint.billing.domain.PaymentMethod m) {
            return new PaymentMethodView(m.getId(), m.getGateway(), m.getBrand(), m.getLast4(),
                    m.getStatus().name(), m.getCreatedAt());
        }
    }

    public record SavePaymentMethodRequest(@NotBlank String gatewayToken, String gatewayCustomerId,
                                           String brand, String last4) { }

    // ---- requests ---------------------------------------------------

    public record CreatePlanRequest(@NotNull SubjectType target, @NotBlank String code, @NotBlank String name,
                                    String description, SubscriptionPlan.BillingCycle billingCycle,
                                    BigDecimal price, Map<String, Long> entitlements, Integer sortOrder) { }

    public record UpdatePlanRequest(String name, String description, BigDecimal price,
                                    Map<String, Long> entitlements, Boolean active, Integer sortOrder) { }

    public record AssignSubscriptionRequest(@NotNull SubjectType subjectType, @NotNull UUID subjectId,
                                            @NotBlank String planCode, boolean comp) { }

    public record SelfPlanRequest(@NotBlank String planCode) { }

    public record TierRequest(@NotBlank String tier) { }

    public record PayLinkResponse(String paymentLink) { }
}
