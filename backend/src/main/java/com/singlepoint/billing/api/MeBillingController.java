package com.singlepoint.billing.api;

import com.singlepoint.billing.BillingService;
import com.singlepoint.billing.PaymentMethodRepository;
import com.singlepoint.billing.domain.PaymentMethod;
import com.singlepoint.billing.domain.SubjectType;
import com.singlepoint.billing.domain.Subscription;
import com.singlepoint.billing.domain.SubscriptionPlan;
import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.offer.OfferRepository;
import com.singlepoint.provider.ServiceProviderRepository;
import com.singlepoint.security.AppPrincipal;
import com.singlepoint.ticket.TicketRepository;
import com.singlepoint.ticket.TicketService;
import com.singlepoint.user.AppUserRepository;
import com.singlepoint.user.domain.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The signed-in admin's community plan, the provider's listing plan, or a resident's plan. */
@RestController
@RequestMapping("/api/v1/me/billing")
@PreAuthorize("hasAnyRole('ADMIN','PROVIDER','RESIDENT')")
@Tag(name = "Me — Billing", description = "Your plan, usage against limits, and any due invoices")
public class MeBillingController {

    private final BillingService billing;
    private final TicketRepository tickets;
    private final OfferRepository offers;
    private final AppUserRepository users;
    private final ServiceProviderRepository providers;
    private final com.singlepoint.tenant.AdminDirectory adminDirectory;
    private final PaymentMethodRepository paymentMethods;

    public MeBillingController(BillingService billing, TicketRepository tickets, OfferRepository offers,
                              AppUserRepository users, ServiceProviderRepository providers,
                              com.singlepoint.tenant.AdminDirectory adminDirectory,
                              PaymentMethodRepository paymentMethods) {
        this.billing = billing;
        this.tickets = tickets;
        this.offers = offers;
        this.users = users;
        this.providers = providers;
        this.adminDirectory = adminDirectory;
        this.paymentMethods = paymentMethods;
    }

    /** Resolve (subjectType, subjectId) for the caller. */
    private SubjectRef subjectFor(AppPrincipal p) {
        if (p.getRole() == Role.ADMIN) {
            if (p.getTenantId() == null) throw new AppException(ErrorCode.FORBIDDEN, "No active community");
            return new SubjectRef(SubjectType.TENANT, p.getTenantId());
        }
        if (p.getRole() == Role.RESIDENT) {
            // MVP-13 (A1): resident plans are infrastructure only — RESIDENT_FREE for everyone.
            return new SubjectRef(SubjectType.RESIDENT, p.getUserId());
        }
        UUID providerId = providers.findByUserId(p.getUserId())
                .orElseThrow(() -> AppException.notFound("Service provider")).getId();
        return new SubjectRef(SubjectType.PROVIDER, providerId);
    }

    private record SubjectRef(SubjectType type, UUID id) { }

    private long usage(SubjectRef ref, String feature) {
        var monthStart = TicketService.monthStart();
        return switch (feature) {
            case "TICKETS_PER_MONTH" -> tickets.countByTenantIdAndCreatedAtAfter(ref.id(), monthStart);
            case "ADMIN_SEATS" -> adminDirectory.seatCount(ref.id());
            case "OFFERS_PER_MONTH" -> ref.type() == SubjectType.TENANT
                    ? offers.countByTenantIdAndCreatedAtAfter(ref.id(), monthStart)
                    : offers.countByServiceProviderIdAndCreatedAtAfter(ref.id(), monthStart);
            case "DIRECTORY_LISTING", "WHATSAPP_NOTIFICATIONS" -> 1L;
            default -> 0L;
        };
    }

    @GetMapping
    @Operation(summary = "Current plan, usage vs limits, due invoices, and upgrade options")
    @Transactional(readOnly = true)
    public ResponseEntity<BillingDtos.MyBillingView> get(@AuthenticationPrincipal AppPrincipal p) {
        SubjectRef ref = subjectFor(p);
        SubscriptionPlan plan = billing.effectivePlan(ref.type(), ref.id());
        Subscription sub = billing.activeSubscription(ref.type(), ref.id()).orElse(null);

        List<BillingDtos.UsageView> usage = new ArrayList<>();
        plan.entitlementMap().forEach((feature, limit) ->
                usage.add(new BillingDtos.UsageView(feature, limit, usage(ref, feature))));

        List<BillingDtos.InvoiceView> due = billing.dueInvoicesFor(ref.type(), ref.id()).stream()
                .map(BillingDtos.InvoiceView::of).toList();

        List<BillingDtos.PlanView> upgrades = billing.listPlans(true).stream()
                .filter(pl -> pl.getTarget() == ref.type())
                .map(BillingDtos.PlanView::of).toList();

        var subView = sub != null ? BillingDtos.SubscriptionView.of(sub, billing.planById(sub.getPlanId())) : null;
        String tier = ref.type() == SubjectType.PROVIDER
                ? providers.findById(ref.id()).map(sp -> sp.getTier().name()).orElse(null) : null;

        PaymentMethod pm = paymentMethods
                .findFirstByUserIdAndStatusOrderByCreatedAtDesc(p.getUserId(), PaymentMethod.Status.ACTIVE).orElse(null);
        boolean recurring = sub != null && sub.getGatewaySubscriptionId() != null;
        var nextChargeAt = recurring ? sub.getCurrentPeriodEnd() : null;

        return ResponseEntity.ok(new BillingDtos.MyBillingView(ref.type().name(), ref.id(), tier,
                BillingDtos.PlanView.of(plan), subView, usage, due, upgrades,
                pm != null ? BillingDtos.PaymentMethodView.of(pm) : null, recurring, nextChargeAt));
    }

    @PostMapping("/plan")
    @Operation(summary = "Switch to a paid plan (creates a due invoice + grace window)")
    public ResponseEntity<BillingDtos.SubscriptionView> selfUpgrade(@AuthenticationPrincipal AppPrincipal p,
            @Valid @RequestBody BillingDtos.SelfPlanRequest body) {
        SubjectRef ref = subjectFor(p);
        if (ref.type() == SubjectType.RESIDENT) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "No resident plans are available yet");
        }
        Subscription s = billing.assignPlan(ref.type(), ref.id(), body.planCode(), false, p.getUserId());
        return ResponseEntity.ok(BillingDtos.SubscriptionView.of(s, billing.planById(s.getPlanId())));
    }

    @PostMapping("/invoices/{id}/pay")
    @Operation(summary = "Get (or create) the payment link for a due invoice")
    public ResponseEntity<BillingDtos.PayLinkResponse> pay(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID id) {
        SubjectRef ref = subjectFor(p);
        // ensure the invoice belongs to the caller's subject
        boolean own = billing.invoicesFor(ref.type(), ref.id()).stream().anyMatch(i -> i.getId().equals(id));
        if (!own) throw AppException.notFound("Invoice");
        return ResponseEntity.ok(new BillingDtos.PayLinkResponse(billing.payLinkFor(id)));
    }
}
