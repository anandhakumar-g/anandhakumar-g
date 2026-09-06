package com.singlepoint.billing.api;

import com.singlepoint.billing.PaymentMethodRepository;
import com.singlepoint.billing.domain.PaymentMethod;
import com.singlepoint.common.error.AppException;
import com.singlepoint.security.AppPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

/** MVP-13 (A2): the caller's saved gateway payment tokens, used to auto-charge subscriptions. */
@RestController
@RequestMapping("/api/v1/me/payment-methods")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Me — Payment methods", description = "Saved gateway tokens for recurring billing")
public class MePaymentMethodController {

    private final PaymentMethodRepository paymentMethods;

    public MePaymentMethodController(PaymentMethodRepository paymentMethods) {
        this.paymentMethods = paymentMethods;
    }

    @GetMapping
    @Operation(summary = "List your saved payment methods")
    public ResponseEntity<List<BillingDtos.PaymentMethodView>> list(@AuthenticationPrincipal AppPrincipal p) {
        return ResponseEntity.ok(paymentMethods
                .findByUserIdAndStatusOrderByCreatedAtDesc(p.getUserId(), PaymentMethod.Status.ACTIVE)
                .stream().map(BillingDtos.PaymentMethodView::of).toList());
    }

    @PostMapping
    @Operation(summary = "Save a gateway token (obtained from the gateway's checkout SDK)")
    @Transactional
    public ResponseEntity<BillingDtos.PaymentMethodView> save(@AuthenticationPrincipal AppPrincipal p,
            @Valid @RequestBody BillingDtos.SavePaymentMethodRequest body) {
        PaymentMethod m = new PaymentMethod();
        m.setUserId(p.getUserId());
        m.setGateway("stub"); // the active gateway; the real name is set by the checkout integration on cloud
        m.setGatewayToken(body.gatewayToken().trim());
        m.setGatewayCustomerId(body.gatewayCustomerId());
        m.setBrand(body.brand());
        m.setLast4(body.last4());
        return ResponseEntity.status(HttpStatus.CREATED).body(BillingDtos.PaymentMethodView.of(paymentMethods.save(m)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove a saved payment method")
    @Transactional
    public ResponseEntity<Void> remove(@AuthenticationPrincipal AppPrincipal p, @PathVariable UUID id) {
        PaymentMethod m = paymentMethods.findById(id)
                .filter(x -> x.getUserId().equals(p.getUserId()))
                .orElseThrow(() -> AppException.notFound("Payment method"));
        m.setStatus(PaymentMethod.Status.REMOVED);
        paymentMethods.save(m);
        return ResponseEntity.noContent().build();
    }
}
