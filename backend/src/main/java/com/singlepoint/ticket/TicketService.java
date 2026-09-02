package com.singlepoint.ticket;

import com.singlepoint.category.CategoryRepository;
import com.singlepoint.category.domain.Category;
import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.flat.FlatRepository;
import com.singlepoint.flat.domain.Flat;
import com.singlepoint.notification.DomainEventPublisher;
import com.singlepoint.provider.ServiceProviderRepository;
import com.singlepoint.provider.TenantServiceProviderRepository;
import com.singlepoint.provider.domain.ServiceProvider;
import com.singlepoint.security.AppPrincipal;
import com.singlepoint.storage.StorageService;
import com.singlepoint.tenant.TenantRepository;
import com.singlepoint.ticket.domain.Ticket;
import com.singlepoint.ticket.domain.TicketAttachment;
import com.singlepoint.ticket.domain.TicketStatus;
import com.singlepoint.ticket.domain.TicketStatusHistory;
import com.singlepoint.user.AppUserRepository;
import com.singlepoint.user.domain.AppUser;
import com.singlepoint.user.domain.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketStatusHistoryRepository historyRepository;
    private final TicketAttachmentRepository attachmentRepository;
    private final CategoryRepository categoryRepository;
    private final FlatRepository flatRepository;
    private final ServiceProviderRepository providerRepository;
    private final TenantServiceProviderRepository tenantProviderRepository;
    private final AppUserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final DomainEventPublisher events;
    private final StorageService storageService;
    private final com.singlepoint.entitlement.EntitlementService entitlements;

    public TicketService(TicketRepository ticketRepository, TicketStatusHistoryRepository historyRepository,
                         TicketAttachmentRepository attachmentRepository, CategoryRepository categoryRepository,
                         FlatRepository flatRepository, ServiceProviderRepository providerRepository,
                         TenantServiceProviderRepository tenantProviderRepository, AppUserRepository userRepository,
                         TenantRepository tenantRepository, DomainEventPublisher events, StorageService storageService,
                         com.singlepoint.entitlement.EntitlementService entitlements) {
        this.ticketRepository = ticketRepository;
        this.historyRepository = historyRepository;
        this.attachmentRepository = attachmentRepository;
        this.categoryRepository = categoryRepository;
        this.flatRepository = flatRepository;
        this.providerRepository = providerRepository;
        this.tenantProviderRepository = tenantProviderRepository;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.events = events;
        this.storageService = storageService;
        this.entitlements = entitlements;
    }

    public static java.time.Instant monthStart() {
        return java.time.YearMonth.now(java.time.ZoneOffset.UTC)
                .atDay(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
    }

    private final TicketStateMachine stateMachine = new TicketStateMachine();

    // ---- raise -------------------------------------------------------------------

    public record RaiseCommand(UUID categoryId, UUID subcategoryId, String description, String priority,
                               String serviceAddressText, BigDecimal serviceGeoLat, BigDecimal serviceGeoLng,
                               String serviceLandmark, String preferredTimeWindow, UUID flatId) { }

    @Transactional
    public Ticket raise(AppPrincipal principal, RaiseCommand cmd) {
        if (principal.getRole() != Role.RESIDENT) {
            throw new AppException(ErrorCode.FORBIDDEN, "Only residents can raise tickets");
        }
        UUID tenantId = requireTenant(principal);
        entitlements.requireWithinQuota(com.singlepoint.billing.domain.SubjectType.TENANT, tenantId,
                "TICKETS_PER_MONTH", ticketRepository.countByTenantIdAndCreatedAtAfter(tenantId, monthStart()));
        Category category = categoryRepository.findById(cmd.categoryId())
                .orElseThrow(() -> AppException.notFound("Category"));

        Flat flat = null;
        if (cmd.flatId() != null) {
            flat = flatRepository.findByIdAndTenantId(cmd.flatId(), tenantId)
                    .orElseThrow(() -> AppException.notFound("Flat"));
        }

        String address = cmd.serviceAddressText();
        if ((address == null || address.isBlank()) && flat != null) {
            address = flat.getAddressText() != null ? flat.getAddressText() : flat.label();
        }
        if (address == null || address.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "A service location is required");
        }

        Ticket t = new Ticket();
        t.setTenantId(tenantId);
        t.setReferenceCode("SP-" + ticketRepository.nextReferenceSequence());
        t.setRaisedByUserId(principal.getUserId());
        t.setFlatId(flat != null ? flat.getId() : null);
        t.setCategoryId(category.getId());
        t.setSubcategoryId(cmd.subcategoryId());
        t.setDescription(cmd.description());
        if (cmd.priority() != null && !cmd.priority().isBlank()) {
            t.setPriority(Ticket.Priority.valueOf(cmd.priority().toUpperCase()));
        }
        t.setServiceAddressText(address);
        t.setServiceGeoLat(cmd.serviceGeoLat());
        t.setServiceGeoLng(cmd.serviceGeoLng());
        t.setServiceLandmark(cmd.serviceLandmark());
        t.setPreferredTimeWindow(cmd.preferredTimeWindow());
        if (category.getSlaHours() != null) {
            t.setSlaDueAt(Instant.now().plus(category.getSlaHours(), ChronoUnit.HOURS));
        }
        t.setStatus(TicketStatus.NEW);
        ticketRepository.save(t);

        recordHistory(t, null, TicketStatus.NEW, principal.getUserId(), Role.RESIDENT, "Ticket raised");
        notifyAdmins(t, "New ticket " + t.getReferenceCode(),
                category.getName() + ": " + shorten(t.getDescription()));
        return t;
    }

    @Transactional
    public TicketAttachment addAttachment(AppPrincipal principal, UUID ticketId, String filename,
                                          String contentType, byte[] content) {
        Ticket t = loadForActor(principal, ticketId);
        if (!t.getRaisedByUserId().equals(principal.getUserId()) && principal.getRole() != Role.ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN, "Cannot attach files to this ticket");
        }
        String key = storageService.put("tenants/" + t.getTenantId() + "/tickets/" + t.getId(),
                filename, contentType, content);
        TicketAttachment a = new TicketAttachment();
        a.setTenantId(t.getTenantId());
        a.setTicketId(t.getId());
        a.setStorageKey(key);
        a.setContentType(contentType);
        a.setSizeBytes(content.length);
        a.setOriginalFilename(filename);
        a.setUploadedByUserId(principal.getUserId());
        return attachmentRepository.save(a);
    }

    // ---- admin actions ---------------------------------------------------------

    @Transactional
    public Ticket acknowledge(AppPrincipal principal, UUID ticketId, String remarks) {
        requireRole(principal, Role.ADMIN);
        Ticket t = loadForActor(principal, ticketId);
        transition(t, TicketStatus.ACKNOWLEDGED, principal, Role.ADMIN, remarks);
        t.setAcknowledgedAt(Instant.now());
        ticketRepository.save(t);
        notifyUser(t, t.getRaisedByUserId(), "Ticket " + t.getReferenceCode() + " acknowledged",
                "Your community team has seen your request.");
        return t;
    }

    @Transactional
    public Ticket resolveDirect(AppPrincipal principal, UUID ticketId, String resolutionNotes) {
        requireRole(principal, Role.ADMIN);
        Ticket t = loadForActor(principal, ticketId);
        transition(t, TicketStatus.RESOLVED, principal, Role.ADMIN, resolutionNotes);
        t.setResolutionNotes(resolutionNotes);
        t.setResolvedAt(Instant.now());
        ticketRepository.save(t);
        notifyUser(t, t.getRaisedByUserId(), "Ticket " + t.getReferenceCode() + " resolved",
                "Marked resolved by your community team.");
        return t;
    }

    @Transactional
    public Ticket assign(AppPrincipal principal, UUID ticketId, UUID providerId, String remarks) {
        requireRole(principal, Role.ADMIN);
        Ticket t = loadForActor(principal, ticketId);
        ServiceProvider provider = requireAssignableProvider(t.getTenantId(), providerId);
        stateMachine.assertTransition(t.getStatus(), TicketStatus.ASSIGNED, Role.ADMIN);
        TicketStatus from = t.getStatus();
        t.setAssignedProviderId(provider.getId());
        t.setAllocationApprovedByResident(false);
        t.setStatus(TicketStatus.ASSIGNED);
        t.setAssignedAt(Instant.now());
        ticketRepository.save(t);
        recordHistory(t, from, TicketStatus.ASSIGNED, principal.getUserId(), Role.ADMIN,
                (from == TicketStatus.ASSIGNED || from == TicketStatus.REJECTED ? "Rerouted to " : "Assigned to ")
                        + provider.getName() + (remarks != null ? " — " + remarks : ""));
        if (provider.getUserId() != null) {
            notifyUser(t, provider.getUserId(), "New job " + t.getReferenceCode(),
                    shorten(t.getDescription()) + " at " + t.getServiceAddressText());
        }
        notifyUser(t, t.getRaisedByUserId(), "Ticket " + t.getReferenceCode() + " assigned",
                provider.getName() + " has been assigned to your request.");
        return t;
    }

    // ---- provider actions ----------------------------------------------------

    @Transactional
    public Ticket providerRespond(AppPrincipal principal, UUID ticketId, boolean accept, String reason) {
        ServiceProvider provider = requireProvider(principal);
        Ticket t = loadForActor(principal, ticketId);
        ensureAssignedTo(t, provider);
        TicketStatus to = accept ? TicketStatus.ACCEPTED : TicketStatus.REJECTED;
        transition(t, to, principal, Role.PROVIDER, reason);
        ticketRepository.save(t);
        String msg = accept ? provider.getName() + " accepted the job."
                            : provider.getName() + " could not take this job" + (reason != null ? ": " + reason : ".");
        notifyUser(t, t.getRaisedByUserId(), "Ticket " + t.getReferenceCode() + (accept ? " accepted" : " needs reassignment"), msg);
        if (!accept) notifyAdmins(t, "Ticket " + t.getReferenceCode() + " rejected by provider", msg);
        return t;
    }

    @Transactional
    public Ticket providerStatus(AppPrincipal principal, UUID ticketId, String toStatusRaw,
                                 String reason, String resolutionNotes) {
        ServiceProvider provider = requireProvider(principal);
        Ticket t = loadForActor(principal, ticketId);
        ensureAssignedTo(t, provider);
        TicketStatus to = TicketStatus.valueOf(toStatusRaw.toUpperCase());
        transition(t, to, principal, Role.PROVIDER, reason != null ? reason : resolutionNotes);
        if (to == TicketStatus.ON_HOLD) t.setHoldReason(reason);
        if (to == TicketStatus.RESOLVED) {
            t.setResolutionNotes(resolutionNotes);
            t.setResolvedAt(Instant.now());
        }
        ticketRepository.save(t);
        notifyUser(t, t.getRaisedByUserId(), "Ticket " + t.getReferenceCode() + " — " + to,
                switch (to) {
                    case IN_PROGRESS -> provider.getName() + " has started work.";
                    case ON_HOLD -> "Work is on hold" + (reason != null ? ": " + reason : ".");
                    case RESOLVED -> "Marked resolved" + (resolutionNotes != null ? ": " + resolutionNotes : ".")
                            + " You can confirm or reopen it.";
                    default -> "Status updated to " + to;
                });
        return t;
    }

    // ---- resident actions --------------------------------------------------

    @Transactional
    public Ticket reopen(AppPrincipal principal, UUID ticketId, String remarks) {
        Ticket t = loadForActor(principal, ticketId);
        requireRaiser(principal, t);
        if (t.getResolvedAt() != null) {
            int windowHours = tenantRepository.findById(t.getTenantId())
                    .map(x -> x.getReopenWindowHours()).orElse(72);
            if (t.getResolvedAt().plus(windowHours, ChronoUnit.HOURS).isBefore(Instant.now())) {
                throw new AppException(ErrorCode.CONFLICT, "The reopen window for this ticket has passed");
            }
        }
        transition(t, TicketStatus.REOPENED, principal, Role.RESIDENT, remarks);
        t.setReopenedCount(t.getReopenedCount() + 1);
        t.setResolvedAt(null);
        ticketRepository.save(t);
        notifyAdmins(t, "Ticket " + t.getReferenceCode() + " reopened",
                (remarks != null ? remarks : "Resident reopened the ticket."));
        if (t.getAssignedProviderId() != null) {
            providerRepository.findById(t.getAssignedProviderId())
                    .filter(p -> p.getUserId() != null)
                    .ifPresent(p -> notifyUser(t, p.getUserId(), "Job " + t.getReferenceCode() + " reopened",
                            remarks != null ? remarks : "Resident reopened the ticket."));
        }
        return t;
    }

    @Transactional
    public Ticket close(AppPrincipal principal, UUID ticketId, Integer rating, String remarks) {
        Ticket t = loadForActor(principal, ticketId);
        requireRaiser(principal, t);
        transition(t, TicketStatus.CLOSED, principal, Role.RESIDENT, remarks);
        t.setClosedAt(Instant.now());
        if (rating != null) {
            if (rating < 1 || rating > 5) throw new AppException(ErrorCode.VALIDATION_FAILED, "rating must be 1-5");
            t.setRating(rating);
            t.setRatingComment(remarks);
        }
        ticketRepository.save(t);
        return t;
    }

    // ---- reads --------------------------------------------------------------

    @Transactional(readOnly = true)
    public Ticket getForActor(AppPrincipal principal, UUID ticketId) {
        return loadForActor(principal, ticketId);
    }

    @Transactional(readOnly = true)
    public List<TicketStatusHistory> timeline(AppPrincipal principal, UUID ticketId) {
        loadForActor(principal, ticketId);
        return historyRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    @Transactional(readOnly = true)
    public List<TicketAttachment> attachments(AppPrincipal principal, UUID ticketId) {
        loadForActor(principal, ticketId);
        return attachmentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    @Transactional(readOnly = true)
    public Page<Ticket> list(AppPrincipal principal, String statusFilter, Pageable pageable) {
        UUID tenantId = requireTenant(principal);
        TicketStatus status = (statusFilter != null && !statusFilter.isBlank())
                ? TicketStatus.valueOf(statusFilter.toUpperCase()) : null;
        return switch (principal.getRole()) {
            case RESIDENT -> ticketRepository.findByRaisedByUserIdOrderByCreatedAtDesc(principal.getUserId(), pageable);
            case ADMIN -> status != null
                    ? ticketRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status, pageable)
                    : ticketRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
            case PROVIDER -> ticketRepository.findByAssignedProviderIdOrderByCreatedAtDesc(
                    requireProvider(principal).getId(), pageable);
            default -> throw new AppException(ErrorCode.FORBIDDEN, "No ticket access for this role");
        };
    }

    // ---- internals -------------------------------------------------------

    private void transition(Ticket t, TicketStatus to, AppPrincipal principal, Role actorRole, String remarks) {
        stateMachine.assertTransition(t.getStatus(), to, actorRole);
        TicketStatus from = t.getStatus();
        t.setStatus(to);
        recordHistory(t, from, to, principal != null ? principal.getUserId() : null, actorRole, remarks);
    }

    private void recordHistory(Ticket t, TicketStatus from, TicketStatus to, UUID userId, Role role, String remarks) {
        TicketStatusHistory h = new TicketStatusHistory();
        h.setTenantId(t.getTenantId());
        h.setTicketId(t.getId());
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setChangedByUserId(userId);
        h.setActorRole(role != null ? role.name() : "SYSTEM");
        h.setRemarks(remarks);
        historyRepository.save(h);
    }

    private Ticket loadForActor(AppPrincipal principal, UUID ticketId) {
        UUID tenantId = requireTenant(principal);
        Ticket t = ticketRepository.findByIdAndTenantId(ticketId, tenantId)
                .orElseThrow(() -> AppException.notFound("Ticket"));
        switch (principal.getRole()) {
            case RESIDENT -> {
                if (!t.getRaisedByUserId().equals(principal.getUserId())) {
                    throw AppException.notFound("Ticket");
                }
            }
            case PROVIDER -> {
                ServiceProvider p = requireProvider(principal);
                if (!p.getId().equals(t.getAssignedProviderId())) {
                    throw AppException.notFound("Ticket");
                }
            }
            case ADMIN -> { /* full tenant visibility */ }
            default -> throw new AppException(ErrorCode.FORBIDDEN, "No ticket access for this role");
        }
        return t;
    }

    private UUID requireTenant(AppPrincipal principal) {
        if (principal.getTenantId() == null) {
            throw new AppException(ErrorCode.FORBIDDEN, "Join a community first");
        }
        return principal.getTenantId();
    }

    private void requireRole(AppPrincipal principal, Role role) {
        if (principal.getRole() != role) throw new AppException(ErrorCode.FORBIDDEN, "Requires " + role + " role");
    }

    private void requireRaiser(AppPrincipal principal, Ticket t) {
        if (!t.getRaisedByUserId().equals(principal.getUserId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "Only the resident who raised this ticket can do that");
        }
    }

    private ServiceProvider requireProvider(AppPrincipal principal) {
        return providerRepository.findByUserId(principal.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN, "No provider profile for this account"));
    }

    private ServiceProvider requireAssignableProvider(UUID tenantId, UUID providerId) {
        ServiceProvider p = providerRepository.findById(providerId)
                .orElseThrow(() -> AppException.notFound("Service provider"));
        if (!p.isAssignable()) {
            throw new AppException(ErrorCode.PROVIDER_NOT_ASSIGNABLE,
                    "Provider must be verified and active before assignment");
        }
        var pSubject = com.singlepoint.billing.domain.SubjectType.PROVIDER;
        if (!entitlements.isEntitled(pSubject, p.getId(), "DIRECTORY_LISTING")
                || entitlements.isLapsed(pSubject, p.getId())) {
            throw new AppException(ErrorCode.PROVIDER_NOT_ASSIGNABLE,
                    "Provider's listing plan is not active");
        }
        if (!tenantProviderRepository.existsByTenantIdAndServiceProviderId(tenantId, providerId)) {
            throw new AppException(ErrorCode.PROVIDER_NOT_ASSIGNABLE,
                    "Provider is not enrolled in this community");
        }
        return p;
    }

    private void ensureAssignedTo(Ticket t, ServiceProvider provider) {
        if (!provider.getId().equals(t.getAssignedProviderId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "This job is not assigned to you");
        }
    }

    private void notifyAdmins(Ticket t, String title, String body) {
        List<UUID> admins = userRepository.findByRoleAndCurrentTenantId(Role.ADMIN, t.getTenantId())
                .stream().map(AppUser::getId).collect(Collectors.toList());
        if (!admins.isEmpty()) {
            events.publish("TICKET_" + t.getStatus(), "ticket", t.getId(), t.getTenantId(),
                    admins, title, body, ticketData(t));
        }
    }

    private void notifyUser(Ticket t, UUID userId, String title, String body) {
        if (userId == null) return;
        events.publish("TICKET_" + t.getStatus(), "ticket", t.getId(), t.getTenantId(),
                List.of(userId), title, body, ticketData(t));
    }

    private Map<String, Object> ticketData(Ticket t) {
        return Map.of("ticketId", t.getId().toString(),
                "reference", t.getReferenceCode(),
                "status", t.getStatus().name());
    }

    private static String shorten(String s) {
        if (s == null) return "";
        return s.length() <= 80 ? s : s.substring(0, 77) + "...";
    }
}
