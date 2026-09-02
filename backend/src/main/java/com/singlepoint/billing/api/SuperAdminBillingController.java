package com.singlepoint.billing.api;

import com.singlepoint.billing.BillingService;
import com.singlepoint.billing.domain.SubjectType;
import com.singlepoint.billing.domain.Subscription;
import com.singlepoint.billing.domain.SubscriptionInvoice;
import com.singlepoint.provider.ProviderService;
import com.singlepoint.provider.domain.ProviderTier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/superadmin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Super Admin — Billing", description = "Subscription plans, subscriptions, invoices, featured tier")
public class SuperAdminBillingController {

    private final BillingService billing;
    private final ProviderService providerService;

    public SuperAdminBillingController(BillingService billing, ProviderService providerService) {
        this.billing = billing;
        this.providerService = providerService;
    }

    private BillingDtos.SubscriptionView view(Subscription s) {
        return BillingDtos.SubscriptionView.of(s, billing.planById(s.getPlanId()));
    }

    // ---- plans -------------------------------------------------------

    @GetMapping("/plans")
    @Operation(summary = "All plans (add ?activeOnly=true for the sellable set)")
    public ResponseEntity<List<BillingDtos.PlanView>> plans(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(billing.listPlans(activeOnly).stream().map(BillingDtos.PlanView::of).toList());
    }

    @PostMapping("/plans")
    @Operation(summary = "Create a subscription plan")
    public ResponseEntity<BillingDtos.PlanView> createPlan(@Valid @RequestBody BillingDtos.CreatePlanRequest body) {
        var p = billing.createPlan(body.target(), body.code(), body.name(), body.description(),
                body.billingCycle(), body.price(), body.entitlements(), body.sortOrder());
        return ResponseEntity.status(HttpStatus.CREATED).body(BillingDtos.PlanView.of(p));
    }

    @PutMapping("/plans/{id}")
    @Operation(summary = "Update a plan's price / entitlements / visibility")
    public ResponseEntity<BillingDtos.PlanView> updatePlan(@PathVariable UUID id,
            @Valid @RequestBody BillingDtos.UpdatePlanRequest body) {
        var p = billing.updatePlan(id, body.name(), body.description(), body.price(),
                body.entitlements(), body.active(), body.sortOrder());
        return ResponseEntity.ok(BillingDtos.PlanView.of(p));
    }

    // ---- subscriptions --------------------------------------------

    @GetMapping("/subscriptions")
    @Operation(summary = "Subscriptions, filterable by subjectType and status")
    public ResponseEntity<List<BillingDtos.SubscriptionView>> subscriptions(
            @RequestParam(required = false) SubjectType subjectType,
            @RequestParam(required = false) String status) {
        Subscription.Status st = status != null ? Subscription.Status.valueOf(status.toUpperCase()) : null;
        return ResponseEntity.ok(billing.listSubscriptions(subjectType, st).stream().map(this::view).toList());
    }

    @PostMapping("/subscriptions")
    @Operation(summary = "Assign a plan to a tenant or provider (comp = free of charge)")
    public ResponseEntity<BillingDtos.SubscriptionView> assign(
            @Valid @RequestBody BillingDtos.AssignSubscriptionRequest body) {
        Subscription s = billing.assignPlan(body.subjectType(), body.subjectId(), body.planCode(), body.comp());
        return ResponseEntity.status(HttpStatus.CREATED).body(view(s));
    }

    @PostMapping("/subscriptions/{id}/cancel")
    public ResponseEntity<BillingDtos.SubscriptionView> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(view(billing.cancel(id)));
    }

    @PostMapping("/subscriptions/{id}/comp")
    public ResponseEntity<BillingDtos.SubscriptionView> comp(@PathVariable UUID id) {
        return ResponseEntity.ok(view(billing.comp(id)));
    }

    // ---- invoices -----------------------------------------------

    @GetMapping("/invoices")
    public ResponseEntity<List<BillingDtos.InvoiceView>> invoices(@RequestParam(required = false) String status) {
        SubscriptionInvoice.Status st = status != null
                ? SubscriptionInvoice.Status.valueOf(status.toUpperCase()) : null;
        return ResponseEntity.ok(billing.listInvoices(st).stream().map(BillingDtos.InvoiceView::of).toList());
    }

    @PostMapping("/invoices/{id}/mark-paid")
    @Operation(summary = "Record an out-of-band payment and activate the subscription")
    public ResponseEntity<BillingDtos.InvoiceView> markPaid(@PathVariable UUID id) {
        return ResponseEntity.ok(BillingDtos.InvoiceView.of(billing.markInvoicePaid(id)));
    }

    // ---- featured tier ----------------------------------------

    @PostMapping("/providers/{id}/tier")
    @Operation(summary = "Set a provider's directory tier (STANDARD / FEATURED)")
    public ResponseEntity<Void> setTier(@PathVariable UUID id, @Valid @RequestBody BillingDtos.TierRequest body) {
        providerService.setTier(id, ProviderTier.valueOf(body.tier().toUpperCase()));
        return ResponseEntity.noContent().build();
    }
}
