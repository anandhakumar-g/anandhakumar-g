package com.singlepoint.payment.api;

import com.singlepoint.payment.PaymentReceiptRepository;
import com.singlepoint.payment.PaymentService;
import com.singlepoint.payment.domain.PaymentReceipt;
import com.singlepoint.payment.domain.TicketPayment;
import com.singlepoint.security.AppPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/payment")
@Tag(name = "Payments", description = "Service charge, cash-with-OTP, online payment, receipt")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentReceiptRepository receipts;

    public PaymentController(PaymentService paymentService, PaymentReceiptRepository receipts) {
        this.paymentService = paymentService;
        this.receipts = receipts;
    }

    private PaymentDtos.PaymentView view(TicketPayment p) {
        String rc = p == null ? null : receipts.findByTicketPaymentId(p.getId())
                .map(PaymentReceipt::getReceiptNumber).orElse(null);
        return p == null ? null : PaymentDtos.PaymentView.of(p, rc);
    }

    @GetMapping
    @Operation(summary = "Current payment for the ticket (null if none)")
    public ResponseEntity<PaymentDtos.PaymentView> get(@AuthenticationPrincipal AppPrincipal p,
                                                       @PathVariable UUID ticketId) {
        return ResponseEntity.ok(view(paymentService.getForTicket(p, ticketId)));
    }

    @PostMapping("/charge")
    @PreAuthorize("hasAnyRole('ADMIN','PROVIDER')")
    @Operation(summary = "Add a service charge (admin or the assigned provider)")
    public ResponseEntity<PaymentDtos.PaymentView> charge(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID ticketId, @Valid @RequestBody PaymentDtos.ChargeRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(view(paymentService.charge(p, ticketId, body.amount(), body.note())));
    }

    @PutMapping("/charge")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Adjust an unsettled charge")
    public ResponseEntity<PaymentDtos.PaymentView> adjust(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID ticketId, @Valid @RequestBody PaymentDtos.ChargeRequest body) {
        return ResponseEntity.ok(view(paymentService.adjust(p, ticketId, body.amount(), body.note())));
    }

    @PostMapping("/mode")
    @PreAuthorize("hasRole('RESIDENT')")
    @Operation(summary = "Resident chooses CASH or ONLINE; ONLINE returns a pay link")
    public ResponseEntity<PaymentDtos.PaymentView> mode(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID ticketId, @Valid @RequestBody PaymentDtos.ModeRequest body) {
        var m = TicketPayment.Mode.valueOf(body.mode().toUpperCase());
        return ResponseEntity.ok(view(paymentService.chooseMode(p, ticketId, m)));
    }

    @PostMapping("/cash/collect")
    @PreAuthorize("hasRole('PROVIDER')")
    @Operation(summary = "Provider marks cash collected → OTP goes to the resident")
    public ResponseEntity<PaymentDtos.StartCashResult> collect(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID ticketId) {
        String devOtp = paymentService.startCashCollection(p, ticketId);
        return ResponseEntity.ok(new PaymentDtos.StartCashResult("CASH_PENDING_OTP", devOtp));
    }

    @PostMapping("/cash/confirm")
    @PreAuthorize("hasAnyRole('RESIDENT','PROVIDER')")
    @Operation(summary = "Resident or provider submits the resident's OTP to confirm cash")
    public ResponseEntity<PaymentDtos.PaymentView> confirm(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID ticketId, @Valid @RequestBody PaymentDtos.ConfirmCashRequest body) {
        return ResponseEntity.ok(view(paymentService.confirmCash(p, ticketId, body.otp())));
    }

    @PostMapping("/waive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentDtos.PaymentView> waive(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID ticketId, @Valid @RequestBody PaymentDtos.WaiveRequest body) {
        return ResponseEntity.ok(view(paymentService.waive(p, ticketId, body.reason())));
    }

    @GetMapping("/receipt")
    @Operation(summary = "Digital receipt for a completed payment")
    public ResponseEntity<PaymentDtos.ReceiptView> receipt(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID ticketId) {
        PaymentReceipt r = paymentService.receiptForTicket(p, ticketId);
        return ResponseEntity.ok(PaymentDtos.ReceiptView.of(r, paymentService.shareText(r)));
    }
}
