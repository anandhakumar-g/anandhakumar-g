package com.singlepoint.payment;

import com.singlepoint.auth.OtpService;
import com.singlepoint.auth.domain.OtpChallenge;
import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.notification.DomainEventPublisher;
import com.singlepoint.payment.domain.PaymentEvent;
import com.singlepoint.payment.domain.PaymentReceipt;
import com.singlepoint.payment.domain.TicketPayment;
import com.singlepoint.payment.gateway.PaymentGateway;
import com.singlepoint.provider.ServiceProviderRepository;
import com.singlepoint.provider.domain.ServiceProvider;
import com.singlepoint.security.AppPrincipal;
import com.singlepoint.ticket.TicketRepository;
import com.singlepoint.ticket.domain.Ticket;
import com.singlepoint.ticket.domain.TicketStatus;
import com.singlepoint.user.AppUserRepository;
import com.singlepoint.user.domain.AppUser;
import com.singlepoint.user.domain.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

    private final TicketPaymentRepository payments;
    private final PaymentReceiptRepository receipts;
    private final PaymentEventRepository events;
    private final PaymentGateway gateway;
    private final TicketRepository ticketRepository;
    private final AppUserRepository userRepository;
    private final ServiceProviderRepository providerRepository;
    private final OtpService otpService;
    private final DomainEventPublisher domainEvents;
    private final String currency;

    public PaymentService(TicketPaymentRepository payments, PaymentReceiptRepository receipts,
                          PaymentEventRepository events, PaymentGateway gateway, TicketRepository ticketRepository,
                          AppUserRepository userRepository, ServiceProviderRepository providerRepository,
                          OtpService otpService, DomainEventPublisher domainEvents,
                          @Value("${sp.payment.currency:INR}") String currency) {
        this.payments = payments;
        this.receipts = receipts;
        this.events = events;
        this.gateway = gateway;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.providerRepository = providerRepository;
        this.otpService = otpService;
        this.domainEvents = domainEvents;
        this.currency = currency;
    }

    // ---- charge ------------------------------------------------------------

    @Transactional
    public TicketPayment charge(AppPrincipal actor, UUID ticketId, BigDecimal amount, String note) {
        if (amount == null || amount.signum() <= 0) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "amount must be greater than zero");
        }
        Ticket ticket = requireTicket(actor, ticketId);
        boolean allowed = actor.getRole() == Role.ADMIN
                || (actor.getRole() == Role.PROVIDER && isAssignedProvider(actor, ticket));
        if (!allowed) throw new AppException(ErrorCode.FORBIDDEN, "Only the community admin or the assigned provider can add a charge");
        if (ticket.getStatus() != TicketStatus.RESOLVED && ticket.getStatus() != TicketStatus.CLOSED) {
            throw new AppException(ErrorCode.CONFLICT, "A charge can only be added once the ticket is resolved");
        }
        activePaymentFor(ticketId).ifPresent(p -> {
            throw new AppException(ErrorCode.CONFLICT, "This ticket already has a payment (" + p.getStatus() + ")");
        });

        TicketPayment p = new TicketPayment();
        p.setTenantId(ticket.getTenantId());
        p.setTicketId(ticketId);
        p.setAmount(amount);
        p.setCurrency(currency);
        p.setChargedByUserId(actor.getUserId());
        p.setChargedByRole(actor.getRole().name());
        p.setNote(note);
        payments.save(p);
        events.save(PaymentEvent.of(p, "CHARGED", amount + " " + currency + (note != null ? " — " + note : "")));

        notifyUser(ticket.getRaisedByUserId(), ticket, "Payment due — " + money(p),
                "A service charge was added to ticket " + ticket.getReferenceCode() + ". Choose how to pay.");
        return p;
    }

    @Transactional
    public TicketPayment adjust(AppPrincipal admin, UUID ticketId, BigDecimal amount, String note) {
        requireRole(admin, Role.ADMIN);
        TicketPayment p = requireActivePayment(admin, ticketId);
        if (p.getStatus().isSettled()) throw new AppException(ErrorCode.CONFLICT, "This payment is already settled");
        if (amount != null && amount.signum() > 0) p.setAmount(amount);
        if (note != null) p.setNote(note);
        return payments.save(p);
    }

    // ---- resident chooses how to pay -------------------------------------

    @Transactional
    public TicketPayment chooseMode(AppPrincipal resident, UUID ticketId, TicketPayment.Mode mode) {
        Ticket ticket = requireTicket(resident, ticketId);
        if (!ticket.getRaisedByUserId().equals(resident.getUserId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "Only the resident who raised this ticket can pay");
        }
        TicketPayment p = requireActivePayment(resident, ticketId);
        if (p.getStatus().isSettled()) throw new AppException(ErrorCode.CONFLICT, "This payment is already settled");
        p.setMode(mode);
        if (mode == TicketPayment.Mode.ONLINE) {
            PaymentGateway.PaymentLink link = gateway.createPaymentLink(
                    p.getAmount(), p.getCurrency(), "Ticket " + ticket.getReferenceCode(), p.getId().toString());
            p.setGateway(gateway.name());
            p.setGatewayRef(link.gatewayRef());
            p.setGatewayPaymentLink(link.url());
            events.save(PaymentEvent.of(p, "LINK_CREATED", link.url()));
        } else {
            p.setGateway(null);
            p.setGatewayRef(null);
            p.setGatewayPaymentLink(null);
        }
        return payments.save(p);
    }

    // ---- cash with OTP -------------------------------------------------

    @Transactional
    public String startCashCollection(AppPrincipal provider, UUID ticketId) {
        Ticket ticket = requireTicket(provider, ticketId);
        if (!isAssignedProvider(provider, ticket)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Only the assigned provider can collect cash");
        }
        TicketPayment p = requireActivePayment(provider, ticketId);
        if (p.getStatus().isSettled()) throw new AppException(ErrorCode.CONFLICT, "This payment is already settled");
        p.setMode(TicketPayment.Mode.CASH);
        p.setStatus(TicketPayment.Status.CASH_PENDING_OTP);
        payments.save(p);

        String residentPhone = residentPhone(ticket);
        String devCode = otpService.request(residentPhone, OtpChallenge.Purpose.CASH_PAYMENT);
        events.save(PaymentEvent.of(p, "OTP_SENT", "to resident"));
        notifyUser(ticket.getRaisedByUserId(), ticket, "Confirm your cash payment",
                "Share the code with the technician (or enter it yourself) to confirm you paid " + money(p) + ".");
        return devCode; // non-null only in dev mode
    }

    @Transactional
    public TicketPayment confirmCash(AppPrincipal actor, UUID ticketId, String otp) {
        Ticket ticket = requireTicket(actor, ticketId);
        boolean allowed = ticket.getRaisedByUserId().equals(actor.getUserId())
                || (actor.getRole() == Role.PROVIDER && isAssignedProvider(actor, ticket));
        if (!allowed) throw new AppException(ErrorCode.FORBIDDEN, "You cannot confirm this payment");
        TicketPayment p = requireActivePayment(actor, ticketId);
        if (p.getStatus() != TicketPayment.Status.CASH_PENDING_OTP) {
            throw new AppException(ErrorCode.CONFLICT, "This payment is not awaiting cash confirmation");
        }
        otpService.verify(residentPhone(ticket), otp, OtpChallenge.Purpose.CASH_PAYMENT);

        p.setStatus(TicketPayment.Status.PAID_CASH);
        p.setOtpVerifiedAt(Instant.now());
        p.setPaidAt(Instant.now());
        payments.save(p);
        events.save(PaymentEvent.of(p, "OTP_VERIFIED", "confirmed by " + actor.getRole()));
        issueReceipt(p, ticket);
        return p;
    }

    // ---- online webhook ---------------------------------------------

    @Transactional
    public void handleWebhook(String gatewayName, Map<String, String> headers, String rawBody) {
        PaymentGateway.WebhookResult res = gateway.verifyAndParse(headers, rawBody);
        TicketPayment p = payments.findByGatewayRef(res.gatewayRef()).orElse(null);
        if (p == null) return; // unknown ref — ignore
        events.save(PaymentEvent.of(p, "WEBHOOK", "paid=" + res.paid()));
        if (p.getStatus().isPaid()) return; // idempotent replay
        if (!res.paid()) return;

        Ticket ticket = ticketRepository.findById(p.getTicketId()).orElseThrow();
        p.setStatus(TicketPayment.Status.PAID_ONLINE);
        p.setGatewayPaymentId(res.gatewayPaymentId());
        p.setPaidAt(Instant.now());
        payments.save(p);
        issueReceipt(p, ticket);
    }

    // ---- waive -----------------------------------------------------

    @Transactional
    public TicketPayment waive(AppPrincipal admin, UUID ticketId, String reason) {
        requireRole(admin, Role.ADMIN);
        TicketPayment p = requireActivePayment(admin, ticketId);
        if (p.getStatus().isPaid()) throw new AppException(ErrorCode.CONFLICT, "This payment is already paid");
        p.setStatus(TicketPayment.Status.WAIVED);
        p.setWaivedByUserId(admin.getUserId());
        p.setWaivedReason(reason);
        payments.save(p);
        events.save(PaymentEvent.of(p, "WAIVED", reason));
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
        notifyUser(ticket.getRaisedByUserId(), ticket, "Charge waived",
                "The charge on ticket " + ticket.getReferenceCode() + " was waived by your community team.");
        return p;
    }

    // ---- reads ---------------------------------------------------

    @Transactional(readOnly = true)
    public TicketPayment getForTicket(AppPrincipal actor, UUID ticketId) {
        Ticket ticket = requireTicket(actor, ticketId);
        assertPartyToTicket(actor, ticket);
        return activePaymentFor(ticketId).orElse(null);
    }

    @Transactional(readOnly = true)
    public PaymentReceipt receiptForTicket(AppPrincipal actor, UUID ticketId) {
        Ticket ticket = requireTicket(actor, ticketId);
        assertPartyToTicket(actor, ticket);
        TicketPayment p = requireActivePayment(actor, ticketId);
        return receipts.findByTicketPaymentId(p.getId())
                .orElseThrow(() -> AppException.notFound("Receipt"));
    }

    public String shareText(PaymentReceipt r) {
        return """
               Single Point — Payment Receipt
               %s
               Ticket:   %s
               Amount:   %s %s
               Mode:     %s
               Paid by:  %s
               Paid to:  %s
               Issued:   %s
               """.formatted(r.getReceiptNumber(), r.getTicketReference(),
                r.getCurrency(), r.getAmount().toPlainString(), r.getMode(),
                r.getPayerName(), r.getPayeeName(), r.getIssuedAt());
    }

    // ---- internals ---------------------------------------------

    private void issueReceipt(TicketPayment p, Ticket ticket) {
        if (receipts.findByTicketPaymentId(p.getId()).isPresent()) return;
        AppUser payer = userRepository.findById(ticket.getRaisedByUserId()).orElse(null);
        String payee = ticket.getAssignedProviderId() != null
                ? providerRepository.findById(ticket.getAssignedProviderId())
                    .map(ServiceProvider::getName).orElse("Community team")
                : "Community team";
        PaymentReceipt r = new PaymentReceipt();
        r.setTenantId(p.getTenantId());
        r.setTicketPaymentId(p.getId());
        r.setReceiptNumber(String.format("RCPT-%06d", receipts.nextReceiptSeq()));
        r.setAmount(p.getAmount());
        r.setCurrency(p.getCurrency());
        r.setMode(p.getStatus() == TicketPayment.Status.PAID_CASH ? "CASH" : "ONLINE");
        r.setTicketReference(ticket.getReferenceCode());
        r.setPayerName(payer != null ? payer.getName() : null);
        r.setPayeeName(payee);
        receipts.save(r);
        events.save(PaymentEvent.of(p, "RECEIPT_ISSUED", r.getReceiptNumber()));
        notifyUser(ticket.getRaisedByUserId(), ticket, "Payment received — " + r.getReceiptNumber(),
                money(p) + " for ticket " + ticket.getReferenceCode() + " is confirmed. Receipt available in the app.");
    }

    private Ticket requireTicket(AppPrincipal actor, UUID ticketId) {
        UUID tenantId = actor.getTenantId();
        if (tenantId == null) throw new AppException(ErrorCode.FORBIDDEN, "No active community");
        return ticketRepository.findByIdAndTenantId(ticketId, tenantId)
                .orElseThrow(() -> AppException.notFound("Ticket"));
    }

    private java.util.Optional<TicketPayment> activePaymentFor(UUID ticketId) {
        return payments.findByTicketId(ticketId).stream()
                .filter(p -> p.getStatus() != TicketPayment.Status.FAILED)
                .findFirst();
    }

    private TicketPayment requireActivePayment(AppPrincipal actor, UUID ticketId) {
        requireTicket(actor, ticketId); // enforces tenant scope
        return activePaymentFor(ticketId).orElseThrow(() -> AppException.notFound("Payment"));
    }

    private boolean isAssignedProvider(AppPrincipal actor, Ticket ticket) {
        if (actor.getRole() != Role.PROVIDER || ticket.getAssignedProviderId() == null) return false;
        return providerRepository.findByUserId(actor.getUserId())
                .map(sp -> sp.getId().equals(ticket.getAssignedProviderId())).orElse(false);
    }

    private void assertPartyToTicket(AppPrincipal actor, Ticket ticket) {
        boolean ok = actor.getRole() == Role.ADMIN
                || ticket.getRaisedByUserId().equals(actor.getUserId())
                || isAssignedProvider(actor, ticket);
        if (!ok) throw AppException.notFound("Ticket");
    }

    private void requireRole(AppPrincipal actor, Role role) {
        if (actor.getRole() != role) throw new AppException(ErrorCode.FORBIDDEN, "Requires " + role + " role");
    }

    private String residentPhone(Ticket ticket) {
        return userRepository.findById(ticket.getRaisedByUserId())
                .map(AppUser::getPhone)
                .orElseThrow(() -> AppException.notFound("Resident"));
    }

    private String money(TicketPayment p) {
        return p.getCurrency() + " " + p.getAmount().toPlainString();
    }

    private void notifyUser(UUID userId, Ticket ticket, String title, String body) {
        domainEvents.publish("PAYMENT_" + ticket.getReferenceCode(), "payment", ticket.getId(), ticket.getTenantId(),
                List.of(userId), title, body,
                Map.of("ticketId", ticket.getId().toString(), "reference", ticket.getReferenceCode(), "type", "payment"));
    }
}
