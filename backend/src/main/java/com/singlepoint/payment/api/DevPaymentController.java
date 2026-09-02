package com.singlepoint.payment.api;

import com.singlepoint.payment.PaymentService;
import com.singlepoint.payment.gateway.StubGateway;
import com.singlepoint.security.TenantScopedExecutor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Local-only stand-in for a hosted checkout page. Opening the link shows a "Pay now" button;
 * pressing it produces a correctly-signed synthetic webhook, exactly as the real gateway would.
 * Not registered on the {@code cloud} profile.
 */
@RestController
@RequestMapping("/dev/pay")
@Profile("!cloud")
public class DevPaymentController {

    private final PaymentService paymentService;
    private final StubGateway stubGateway;
    private final TenantScopedExecutor tenantScoped;

    public DevPaymentController(PaymentService paymentService, StubGateway stubGateway,
                               TenantScopedExecutor tenantScoped) {
        this.paymentService = paymentService;
        this.stubGateway = stubGateway;
        this.tenantScoped = tenantScoped;
    }

    @GetMapping(value = "/{ref}", produces = MediaType.TEXT_HTML_VALUE)
    public String page(@PathVariable String ref) {
        return """
               <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
               <body style="font-family:system-ui;max-width:420px;margin:60px auto;text-align:center">
               <h2>Single Point — test checkout</h2>
               <p>Reference <code>%s</code></p>
               <button style="font-size:18px;padding:14px 28px;border:0;border-radius:10px;background:#2563EB;color:#fff"
                 onclick="fetch('/dev/pay/%s/settle',{method:'POST'}).then(()=>document.body.innerHTML='<h2>Paid \\u2713</h2><p>You can close this tab and return to the app.</p>')">
                 Pay now
               </button></body>
               """.formatted(ref, ref);
    }

    @PostMapping("/{ref}/settle")
    public ResponseEntity<Void> settle(@PathVariable String ref) {
        String body = "{\"ref\":\"" + ref + "\",\"paymentId\":\"pay_" + ref + "\",\"paid\":true}";
        Map<String, String> headers = Map.of("x-stub-signature", stubGateway.sign(body));
        tenantScoped.inWildcard(() -> paymentService.handleWebhook("stub", headers, body));
        return ResponseEntity.ok().build();
    }
}
