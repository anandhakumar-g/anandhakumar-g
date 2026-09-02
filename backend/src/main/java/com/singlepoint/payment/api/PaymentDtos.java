package com.singlepoint.payment.api;

import com.singlepoint.payment.domain.PaymentReceipt;
import com.singlepoint.payment.domain.TicketPayment;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PaymentDtos {

    private PaymentDtos() { }

    public record ChargeRequest(@NotNull @Positive BigDecimal amount, String note) { }
    public record ModeRequest(@NotBlank String mode) { }              // CASH | ONLINE
    public record ConfirmCashRequest(@NotBlank String otp) { }
    public record WaiveRequest(@NotBlank String reason) { }

    public record PaymentView(UUID id, BigDecimal amount, String currency, String mode, String status,
                              String note, String payLink, String receiptNumber,
                              Instant paidAt, Instant createdAt) {
        public static PaymentView of(TicketPayment p, String receiptNumber) {
            return new PaymentView(p.getId(), p.getAmount(), p.getCurrency(),
                    p.getMode() != null ? p.getMode().name() : null, p.getStatus().name(),
                    p.getNote(), p.getGatewayPaymentLink(), receiptNumber, p.getPaidAt(), p.getCreatedAt());
        }
    }

    public record ReceiptView(String receiptNumber, BigDecimal amount, String currency, String mode,
                              String ticketReference, String payerName, String payeeName,
                              Instant issuedAt, String shareText) {
        public static ReceiptView of(PaymentReceipt r, String shareText) {
            return new ReceiptView(r.getReceiptNumber(), r.getAmount(), r.getCurrency(), r.getMode(),
                    r.getTicketReference(), r.getPayerName(), r.getPayeeName(), r.getIssuedAt(), shareText);
        }
    }

    public record StartCashResult(String status, String devOtp) { }
}
