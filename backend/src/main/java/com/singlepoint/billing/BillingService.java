package com.singlepoint.billing;

import com.singlepoint.billing.domain.PaymentMethod;
import com.singlepoint.billing.domain.SubjectType;
import com.singlepoint.billing.domain.Subscription;
import com.singlepoint.billing.domain.SubscriptionInvoice;
import com.singlepoint.billing.domain.SubscriptionPlan;
import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.notification.DomainEventPublisher;
import com.singlepoint.payment.gateway.PaymentGateway;
import com.singlepoint.payment.gateway.RecurringGateway;
import com.singlepoint.payment.gateway.WebhookFallback;
import com.singlepoint.provider.ServiceProviderRepository;
import com.singlepoint.user.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BillingService implements WebhookFallback {

    private final SubscriptionPlanRepository plans;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionInvoiceRepository invoices;
    private final PaymentGateway gateway;
    private final RecurringGateway recurringGateway;
    private final PaymentMethodRepository paymentMethods;
    private final DomainEventPublisher events;
    private final AppUserRepository users;
    private final ServiceProviderRepository providers;
    private final com.singlepoint.tenant.AdminDirectory adminDirectory;
    private final int graceDays;
    private final String currency;
    private final java.util.Set<Integer> dunningOffsets;

    public BillingService(SubscriptionPlanRepository plans, SubscriptionRepository subscriptions,
                          SubscriptionInvoiceRepository invoices, PaymentGateway gateway,
                          RecurringGateway recurringGateway,
                          PaymentMethodRepository paymentMethods,
                          DomainEventPublisher events, AppUserRepository users,
                          ServiceProviderRepository providers,
                          com.singlepoint.tenant.AdminDirectory adminDirectory,
                          @Value("${sp.billing.grace-days:7}") int graceDays,
                          @Value("${sp.billing.currency:INR}") String currency,
                          @Value("${sp.billing.dunning-day-offsets:1,3,6}") String dunningOffsetsCsv) {
        this.dunningOffsets = java.util.Arrays.stream(dunningOffsetsCsv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(Integer::parseInt)
                .collect(java.util.stream.Collectors.toSet());
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.invoices = invoices;
        this.gateway = gateway;
        this.recurringGateway = recurringGateway;
        this.paymentMethods = paymentMethods;
        this.events = events;
        this.users = users;
        this.providers = providers;
        this.adminDirectory = adminDirectory;
        this.graceDays = graceDays;
        this.currency = currency;
    }

    private List<UUID> recipientsFor(SubjectType type, UUID subjectId) {
        List<UUID> out = new ArrayList<>();
        if (type == SubjectType.TENANT) {
            out.addAll(adminDirectory.adminUserIds(subjectId));
        } else {
            providers.findById(subjectId).map(sp -> sp.getUserId()).ifPresent(id -> { if (id != null) out.add(id); });
        }
        return out;
    }

    // ---- plan resolution ------------------------------------------------

    @Transactional(readOnly = true)
    public SubscriptionPlan effectivePlan(SubjectType type, UUID subjectId) {
        Subscription s = activeSubscription(type, subjectId).orElse(null);
        if (s != null && s.getStatus().planActive()) {
            return plans.findById(s.getPlanId()).orElseGet(() -> defaultPlan(type));
        }
        return defaultPlan(type);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Subscription> activeSubscription(SubjectType type, UUID subjectId) {
        return subscriptions.findBySubjectTypeAndSubjectIdAndStatusNot(type, subjectId, Subscription.Status.CANCELLED);
    }

    @Transactional(readOnly = true)
    public SubscriptionPlan planById(UUID planId) {
        return planId == null ? null : plans.findById(planId).orElse(null);
    }

    public SubscriptionPlan defaultPlan(SubjectType type) {
        return plans.findByTargetAndDefaultPlanTrue(type)
                .orElseThrow(() -> new AppException(ErrorCode.INTERNAL, "No default plan for " + type));
    }

    @Transactional(readOnly = true)
    public SubscriptionPlan requirePlan(String code) {
        return plans.findByCode(code).orElseThrow(() -> AppException.notFound("Plan"));
    }

    // ---- admin reads / plan CRUD -------------------------------------

    @Transactional(readOnly = true)
    public List<SubscriptionPlan> listPlans(boolean activeOnly) {
        return activeOnly ? plans.findByActiveTrueOrderByTargetAscSortOrderAsc()
                : plans.findAllByOrderByTargetAscSortOrderAsc();
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private static String toJson(Map<String, Long> map) {
        try {
            return JSON.writeValueAsString(map == null ? Map.of() : map);
        } catch (Exception e) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Invalid entitlements map");
        }
    }

    @Transactional
    public SubscriptionPlan createPlan(SubjectType target, String code, String name, String description,
                                       SubscriptionPlan.BillingCycle cycle, java.math.BigDecimal price,
                                       Map<String, Long> entitlements, Integer sortOrder) {
        plans.findByCode(code).ifPresent(p -> {
            throw new AppException(ErrorCode.CONFLICT, "A plan with code " + code + " already exists");
        });
        SubscriptionPlan p = new SubscriptionPlan();
        p.setTarget(target);
        p.setCode(code);
        p.setName(name);
        p.setDescription(description);
        p.setBillingCycle(cycle != null ? cycle : SubscriptionPlan.BillingCycle.MONTHLY);
        p.setPriceAmount(price != null ? price : java.math.BigDecimal.ZERO);
        p.setCurrency(currency);
        p.setEntitlements(toJson(entitlements));
        if (sortOrder != null) p.setSortOrder(sortOrder);
        return plans.save(p);
    }

    @Transactional
    public SubscriptionPlan updatePlan(UUID planId, String name, String description, java.math.BigDecimal price,
                                       Map<String, Long> entitlements, Boolean active, Integer sortOrder) {
        SubscriptionPlan p = plans.findById(planId).orElseThrow(() -> AppException.notFound("Plan"));
        if (name != null) p.setName(name);
        if (description != null) p.setDescription(description);
        if (price != null) p.setPriceAmount(price);
        if (entitlements != null) p.setEntitlements(toJson(entitlements));
        if (active != null) p.setActive(active);
        if (sortOrder != null) p.setSortOrder(sortOrder);
        return plans.save(p);
    }

    @Transactional(readOnly = true)
    public List<Subscription> listSubscriptions(SubjectType type, Subscription.Status status) {
        List<Subscription> all = (type != null) ? subscriptions.findBySubjectType(type) : subscriptions.findAll();
        return all.stream()
                .filter(s -> status == null || s.getStatus() == status)
                .sorted(java.util.Comparator.comparing(Subscription::getCreatedAt).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public Subscription getSubscription(UUID id) {
        return requireSubscription(id);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionInvoice> listInvoices(SubscriptionInvoice.Status status) {
        return status != null ? invoices.findByStatusOrderByCreatedAtDesc(status)
                : invoices.findAll().stream()
                    .sorted(java.util.Comparator.comparing(SubscriptionInvoice::getCreatedAt).reversed()).toList();
    }

    @Transactional(readOnly = true)
    public List<SubscriptionInvoice> invoicesFor(SubjectType type, UUID subjectId) {
        return invoices.findBySubjectTypeAndSubjectIdOrderByCreatedAtDesc(type, subjectId);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionInvoice> dueInvoicesFor(SubjectType type, UUID subjectId) {
        return invoices.findBySubjectTypeAndSubjectIdAndStatus(type, subjectId, SubscriptionInvoice.Status.DUE);
    }

    // ---- assign / change ------------------------------------------------

    @Transactional
    public Subscription assignPlan(SubjectType type, UUID subjectId, String planCode, boolean comp) {
        return assignPlan(type, subjectId, planCode, comp, null);
    }

    /**
     * @param payingUserId the user whose saved payment method should auto-charge this plan
     *                     (from {@code selfUpgrade}); null for a Super-Admin-assigned plan,
     *                     which stays on the manual pay-link flow.
     */
    @Transactional
    public Subscription assignPlan(SubjectType type, UUID subjectId, String planCode, boolean comp, UUID payingUserId) {
        SubscriptionPlan plan = requirePlan(planCode);
        if (plan.getTarget() != type) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Plan " + planCode + " does not apply to a " + type);
        }
        Subscription sub = activeSubscription(type, subjectId).orElseGet(() -> {
            Subscription s = new Subscription();
            s.setSubjectType(type);
            s.setSubjectId(subjectId);
            return s;
        });
        sub.setPlanId(plan.getId());
        sub.setCancelledAt(null);

        Instant now = Instant.now();
        sub.setCurrentPeriodStart(now);
        sub.setCurrentPeriodEnd(periodEnd(now, plan));

        if (plan.isFree()) {
            sub.setStatus(Subscription.Status.ACTIVE);
            sub.setGraceUntil(null);
            releaseGatewaySubscription(sub);
            voidDueInvoices(sub);
            return subscriptions.save(sub);
        }
        if (comp) {
            sub.setStatus(Subscription.Status.COMPED);
            sub.setGraceUntil(null);
            releaseGatewaySubscription(sub);
            voidDueInvoices(sub);
            return subscriptions.save(sub);
        }
        // paid, not comped: limits apply now, but there's a grace window to pay
        sub.setStatus(Subscription.Status.PAST_DUE);
        sub.setGraceUntil(now.plusSeconds(graceDays * 86400L));

        // MVP-13 (A2): with a saved payment method, create a gateway mandate — the
        // subscription.charged webhook then settles the invoice and flips it ACTIVE.
        PaymentMethod pm = payingUserId == null ? null
                : paymentMethods.findFirstByUserIdAndStatusOrderByCreatedAtDesc(
                        payingUserId, PaymentMethod.Status.ACTIVE).orElse(null);
        if (pm != null && sub.getGatewaySubscriptionId() == null) {
            long amountMinor = plan.getPriceAmount().movePointRight(2).longValueExact();
            RecurringGateway.Mandate m = recurringGateway.createSubscription(
                    pm.getGatewayCustomerId() != null ? pm.getGatewayCustomerId() : pm.getGatewayToken(),
                    plan.getCode(), amountMinor, currency);
            sub.setGatewaySubscriptionId(m.gatewaySubscriptionId());
            sub.setGatewayCustomerId(m.gatewayCustomerId());
            sub.setPendingMandateUrl(m.authUrl());
        }

        subscriptions.save(sub);
        createInvoice(sub, plan, sub.getCurrentPeriodStart(), sub.getCurrentPeriodEnd());
        return sub;
    }

    /**
     * MVP-13 (A3): switch plans with mid-period proration. Paid → paid credits the unused days
     * of the old plan against the new plan's first invoice (net-zero → activate immediately).
     * Paid → free / first-ever paid have nothing to prorate and fall through to {@link #assignPlan}.
     */
    @Transactional
    public Subscription changePlan(SubjectType type, UUID subjectId, String newPlanCode, UUID payingUserId) {
        Subscription current = activeSubscription(type, subjectId).orElse(null);
        SubscriptionPlan oldPlan = current == null ? null : plans.findById(current.getPlanId()).orElse(null);
        SubscriptionPlan newPlan = requirePlan(newPlanCode);

        java.math.BigDecimal credit = java.math.BigDecimal.ZERO;
        if (current != null && oldPlan != null && !oldPlan.isFree() && !newPlan.isFree()
                && current.getStatus().planActive() && current.getCurrentPeriodEnd() != null) {
            long periodDays = Math.max(1, java.time.Duration.between(
                    current.getCurrentPeriodStart(), current.getCurrentPeriodEnd()).toDays());
            long remainingDays = Math.max(0, java.time.Duration.between(
                    Instant.now(), current.getCurrentPeriodEnd()).toDays());
            credit = oldPlan.getPriceAmount()
                    .multiply(java.math.BigDecimal.valueOf(remainingDays))
                    .divide(java.math.BigDecimal.valueOf(periodDays), 2, java.math.RoundingMode.HALF_UP);
        }

        if (current != null) voidDueInvoices(current); // no stacked invoices across a mid-period switch
        Subscription sub = assignPlan(type, subjectId, newPlanCode, false, payingUserId);
        if (credit.signum() > 0) {
            SubscriptionInvoice inv = invoices.findFirstBySubscriptionIdAndStatusOrderByPeriodStartAsc(
                    sub.getId(), SubscriptionInvoice.Status.DUE).orElse(null);
            if (inv != null) {
                if (credit.compareTo(inv.getAmount()) >= 0) {
                    activate(inv); // the credit covers the whole period
                } else {
                    inv.setAmount(inv.getAmount().subtract(credit));
                    invoices.save(inv);
                }
            }
        }
        return sub;
    }

    @Transactional
    public Subscription comp(UUID subscriptionId) {
        Subscription s = requireSubscription(subscriptionId);
        s.setStatus(Subscription.Status.COMPED);
        s.setGraceUntil(null);
        voidDueInvoices(s);
        return subscriptions.save(s);
    }

    @Transactional
    public Subscription cancel(UUID subscriptionId) {
        Subscription s = requireSubscription(subscriptionId);
        s.setStatus(Subscription.Status.CANCELLED);
        s.setCancelledAt(Instant.now());
        voidDueInvoices(s);
        return subscriptions.save(s);
    }

    // ---- invoices -----------------------------------------------------

    @Transactional
    public SubscriptionInvoice createInvoice(Subscription sub, SubscriptionPlan plan, Instant start, Instant end) {
        SubscriptionInvoice inv = new SubscriptionInvoice();
        inv.setSubscriptionId(sub.getId());
        inv.setSubjectType(sub.getSubjectType());
        inv.setSubjectId(sub.getSubjectId());
        inv.setTenantId(sub.getSubjectType() == SubjectType.TENANT ? sub.getSubjectId() : null);
        inv.setAmount(plan.getPriceAmount());
        inv.setCurrency(currency);
        inv.setPeriodStart(start);
        inv.setPeriodEnd(end);
        invoices.save(inv);

        PaymentGateway.PaymentLink link = gateway.createPaymentLink(
                inv.getAmount(), currency, plan.getName() + " subscription", inv.getId().toString());
        inv.setGateway(gateway.name());
        inv.setGatewayRef(link.gatewayRef());
        inv.setGatewayPaymentLink(link.url());
        invoices.save(inv);

        events.publish("SUBSCRIPTION_DUE", "subscription_invoice", inv.getId(),
                sub.getSubjectType() == SubjectType.TENANT ? sub.getSubjectId() : null,
                recipientsFor(sub.getSubjectType(), sub.getSubjectId()), "Subscription payment due",
                currency + " " + inv.getAmount().toPlainString() + " for " + plan.getName(),
                Map.of("invoiceId", inv.getId().toString(), "type", "billing"));
        return inv;
    }

    @Transactional
    public String payLinkFor(UUID invoiceId) {
        SubscriptionInvoice inv = requireInvoice(invoiceId);
        if (inv.getStatus() != SubscriptionInvoice.Status.DUE) {
            throw new AppException(ErrorCode.CONFLICT, "This invoice is not payable");
        }
        if (inv.getGatewayPaymentLink() == null) {
            PaymentGateway.PaymentLink link = gateway.createPaymentLink(
                    inv.getAmount(), inv.getCurrency(), "Subscription", inv.getId().toString());
            inv.setGateway(gateway.name());
            inv.setGatewayRef(link.gatewayRef());
            inv.setGatewayPaymentLink(link.url());
            invoices.save(inv);
        }
        return inv.getGatewayPaymentLink();
    }

    /** Called from the payment webhook fall-through. Returns true if this module recognised the ref. */
    @Override
    @Transactional
    public boolean tryHandle(String gatewayRef, boolean paid, String event) {
        if (event != null && event.startsWith("subscription.")) {
            Subscription sub = subscriptions.findByGatewaySubscriptionId(gatewayRef).orElse(null);
            if (sub == null) return false;
            switch (event) {
                case "subscription.charged" -> {
                    if (paid) {
                        SubscriptionInvoice open = invoices
                                .findFirstBySubscriptionIdAndStatusOrderByPeriodStartAsc(
                                        sub.getId(), SubscriptionInvoice.Status.DUE)
                                .orElse(null);
                        if (open != null) activate(open);
                        else if (!sub.getStatus().planActive() || sub.getStatus() != Subscription.Status.ACTIVE) {
                            sub.setStatus(Subscription.Status.ACTIVE);
                            sub.setGraceUntil(null);
                            subscriptions.save(sub);
                        }
                    }
                }
                case "subscription.halted" -> {
                    sub.setStatus(Subscription.Status.PAST_DUE);
                    sub.setGraceUntil(Instant.now().plusSeconds(graceDays * 86400L));
                    subscriptions.save(sub);
                }
                case "subscription.cancelled" -> {
                    sub.setStatus(Subscription.Status.CANCELLED);
                    sub.setCancelledAt(Instant.now());
                    subscriptions.save(sub);
                }
                default -> { }
            }
            return true;
        }
        SubscriptionInvoice inv = invoices.findByGatewayRef(gatewayRef).orElse(null);
        if (inv == null) return false;
        if (paid && inv.getStatus() != SubscriptionInvoice.Status.PAID) activate(inv);
        return true;
    }

    /** MVP-13 (A2): tell the gateway to stop charging when a subscription goes free / comped / cancelled. */
    private void releaseGatewaySubscription(Subscription sub) {
        if (sub.getGatewaySubscriptionId() != null) {
            try {
                recurringGateway.cancelSubscription(sub.getGatewaySubscriptionId());
            } catch (RuntimeException ignored) {
                // best effort — the daily renewal / webhook path is the backstop
            }
            sub.setGatewaySubscriptionId(null);
        }
    }

    @Transactional
    public SubscriptionInvoice markInvoicePaid(UUID invoiceId) {
        SubscriptionInvoice inv = requireInvoice(invoiceId);
        if (inv.getStatus() != SubscriptionInvoice.Status.PAID) activate(inv);
        return inv;
    }

    private void activate(SubscriptionInvoice inv) {
        inv.setStatus(SubscriptionInvoice.Status.PAID);
        inv.setPaidAt(Instant.now());
        invoices.save(inv);
        Subscription sub = subscriptions.findById(inv.getSubscriptionId()).orElseThrow();
        sub.setStatus(Subscription.Status.ACTIVE);
        sub.setCurrentPeriodStart(inv.getPeriodStart());
        sub.setCurrentPeriodEnd(inv.getPeriodEnd());
        sub.setGraceUntil(null);
        subscriptions.save(sub);
        events.publish("SUBSCRIPTION_ACTIVE", "subscription", sub.getId(),
                sub.getSubjectType() == SubjectType.TENANT ? sub.getSubjectId() : null,
                recipientsFor(sub.getSubjectType(), sub.getSubjectId()), "Subscription active", "Your plan is now active.",
                Map.of("subscriptionId", sub.getId().toString(), "type", "billing"));
    }

    // ---- renewal ------------------------------------------------------

    @Transactional
    public int runRenewal() {
        int changed = 0;
        Instant now = Instant.now();
        for (Subscription s : subscriptions.findByStatusInAndCurrentPeriodEndBefore(
                List.of(Subscription.Status.ACTIVE, Subscription.Status.COMPED, Subscription.Status.TRIAL), now)) {
            SubscriptionPlan plan = plans.findById(s.getPlanId()).orElse(null);
            if (plan == null || plan.isFree()) continue;
            if (s.getStatus() == Subscription.Status.COMPED) {
                // roll a comped period forward, no invoice
                s.setCurrentPeriodStart(s.getCurrentPeriodEnd());
                s.setCurrentPeriodEnd(periodEnd(s.getCurrentPeriodEnd(), plan));
                subscriptions.save(s);
                changed++;
                continue;
            }
            s.setStatus(Subscription.Status.PAST_DUE);
            Instant nextStart = s.getCurrentPeriodEnd();
            Instant nextEnd = periodEnd(nextStart, plan);
            s.setCurrentPeriodStart(nextStart);
            s.setCurrentPeriodEnd(nextEnd);
            s.setGraceUntil(now.plusSeconds(graceDays * 86400L));
            subscriptions.save(s);
            createInvoice(s, plan, nextStart, nextEnd);
            changed++;
        }
        for (Subscription s : subscriptions.findByStatusAndGraceUntilBefore(Subscription.Status.PAST_DUE, now)) {
            s.setStatus(Subscription.Status.EXPIRED);
            subscriptions.save(s);
            events.publish("SUBSCRIPTION_EXPIRED", "subscription", s.getId(),
                    s.getSubjectType() == SubjectType.TENANT ? s.getSubjectId() : null,
                    recipientsFor(s.getSubjectType(), s.getSubjectId()), "Subscription expired",
                    "Your plan has lapsed — the account is read-only until it's renewed.",
                    Map.of("subscriptionId", s.getId().toString(), "type", "billing"));
            changed++;
        }
        // MVP-13 (A3): dunning — one reminder per configured day into the grace window.
        for (Subscription s : subscriptions.findByStatusAndGraceUntilAfter(Subscription.Status.PAST_DUE, now)) {
            if (s.getGraceUntil() == null) continue;
            Instant graceStart = s.getGraceUntil().minusSeconds(graceDays * 86400L);
            long daysIn = java.time.Duration.between(graceStart, now).toDays();
            if (!dunningOffsets.contains((int) daysIn)) continue;
            SubscriptionInvoice due = invoices.findFirstBySubscriptionIdAndStatusOrderByPeriodStartAsc(
                    s.getId(), SubscriptionInvoice.Status.DUE).orElse(null);
            long left = Math.max(0, java.time.Duration.between(now, s.getGraceUntil()).toDays());
            events.publish("SUBSCRIPTION_DUE_REMINDER", "subscription", s.getId(),
                    s.getSubjectType() == SubjectType.TENANT ? s.getSubjectId() : null,
                    recipientsFor(s.getSubjectType(), s.getSubjectId()), "Payment reminder",
                    "Your plan lapses in " + left + " day(s)."
                            + (due != null && due.getGatewayPaymentLink() != null
                               ? " Pay now: " + due.getGatewayPaymentLink() : ""),
                    Map.of("subscriptionId", s.getId().toString(), "type", "billing"));
            changed++;
        }
        return changed;
    }

    // ---- internals --------------------------------------------------

    private Instant periodEnd(Instant from, SubscriptionPlan plan) {
        int months = plan.getBillingCycle() == SubscriptionPlan.BillingCycle.ANNUAL ? 12 : 1;
        return from.atZone(ZoneOffset.UTC).plusMonths(months).toInstant();
    }

    private void voidDueInvoices(Subscription sub) {
        for (SubscriptionInvoice inv : invoices.findBySubjectTypeAndSubjectIdAndStatus(
                sub.getSubjectType(), sub.getSubjectId(), SubscriptionInvoice.Status.DUE)) {
            inv.setStatus(SubscriptionInvoice.Status.VOID);
            invoices.save(inv);
        }
    }

    private Subscription requireSubscription(UUID id) {
        return subscriptions.findById(id).orElseThrow(() -> AppException.notFound("Subscription"));
    }

    private SubscriptionInvoice requireInvoice(UUID id) {
        return invoices.findById(id).orElseThrow(() -> AppException.notFound("Invoice"));
    }
}
