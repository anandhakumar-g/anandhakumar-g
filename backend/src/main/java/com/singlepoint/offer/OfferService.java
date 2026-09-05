package com.singlepoint.offer;

import com.singlepoint.category.VendorCategoryRepository;
import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.notification.DomainEventPublisher;
import com.singlepoint.offer.domain.Offer;
import com.singlepoint.offer.domain.OfferRedemption;
import com.singlepoint.offer.domain.OfferTarget;
import com.singlepoint.provider.ServiceProviderRepository;
import com.singlepoint.provider.domain.ServiceProvider;
import com.singlepoint.security.AppPrincipal;
import com.singlepoint.ticket.TicketRepository;
import com.singlepoint.user.domain.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OfferService {

    private final OfferRepository offerRepository;
    private final OfferTargetRepository targetRepository;
    private final OfferRedemptionRepository redemptionRepository;
    private final OfferFeedbackRepository feedbackRepository;
    private final OfferTargetingService targetingService;
    private final VendorCategoryRepository vendorCategoryRepository;
    private final ServiceProviderRepository providerRepository;
    private final TicketRepository ticketRepository;
    private final com.singlepoint.user.AppUserRepository userRepository;
    private final com.singlepoint.crypto.CryptoService crypto;
    private final DomainEventPublisher events;
    private final com.singlepoint.entitlement.EntitlementService entitlements;
    private final com.singlepoint.observability.AppMetrics metrics;

    public OfferService(OfferRepository offerRepository, OfferTargetRepository targetRepository,
                        OfferRedemptionRepository redemptionRepository, OfferFeedbackRepository feedbackRepository,
                        OfferTargetingService targetingService,
                        VendorCategoryRepository vendorCategoryRepository, ServiceProviderRepository providerRepository,
                        TicketRepository ticketRepository, com.singlepoint.user.AppUserRepository userRepository,
                        com.singlepoint.crypto.CryptoService crypto, DomainEventPublisher events,
                        com.singlepoint.entitlement.EntitlementService entitlements,
                        com.singlepoint.observability.AppMetrics metrics) {
        this.offerRepository = offerRepository;
        this.targetRepository = targetRepository;
        this.redemptionRepository = redemptionRepository;
        this.feedbackRepository = feedbackRepository;
        this.targetingService = targetingService;
        this.vendorCategoryRepository = vendorCategoryRepository;
        this.providerRepository = providerRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.crypto = crypto;
        this.events = events;
        this.entitlements = entitlements;
        this.metrics = metrics;
    }

    public record OfferCommand(UUID vendorCategoryId, String title, String description,
                               String discountType, BigDecimal discountValue, String couponCode,
                               Instant validFrom, Instant validTo, Integer redemptionLimitPerUser,
                               Integer redemptionLimitTotal, String terms) { }

    public record TargetCommand(String targetType, List<String> tenantIds, List<String> userIds,
                                List<String> phones, String enquiryCategoryId, String segmentFilter) { }

    // ---- authoring -----------------------------------------------------------

    @Transactional
    public Offer createDraft(AppPrincipal principal, OfferCommand cmd) {
        vendorCategoryRepository.findById(cmd.vendorCategoryId())
                .orElseThrow(() -> AppException.notFound("Vendor category"));
        Offer o = new Offer();
        o.setCreatedByUserId(principal.getUserId());
        o.setCreatedByRole(principal.getRole().name());
        var monthStart = com.singlepoint.ticket.TicketService.monthStart();
        if (principal.getRole() == Role.PROVIDER) {
            ServiceProvider sp = providerRepository.findByUserId(principal.getUserId())
                    .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN, "No provider profile"));
            o.setServiceProviderId(sp.getId());
            entitlements.requireWithinQuota(com.singlepoint.billing.domain.SubjectType.PROVIDER, sp.getId(),
                    "OFFERS_PER_MONTH", offerRepository.countByServiceProviderIdAndCreatedAtAfter(sp.getId(), monthStart));
        } else if (principal.getRole() == Role.ADMIN) {
            o.setTenantId(principal.getTenantId());
            entitlements.requireWithinQuota(com.singlepoint.billing.domain.SubjectType.TENANT, principal.getTenantId(),
                    "OFFERS_PER_MONTH", offerRepository.countByTenantIdAndCreatedAtAfter(principal.getTenantId(), monthStart));
        } else {
            throw new AppException(ErrorCode.FORBIDDEN, "Only vendors and admins can author offers");
        }
        apply(o, cmd);
        o.setStatus(Offer.Status.DRAFT);
        return offerRepository.save(o);
    }

    @Transactional
    public Offer update(AppPrincipal principal, UUID offerId, OfferCommand cmd) {
        Offer o = ownedEditable(principal, offerId);
        apply(o, cmd);
        return offerRepository.save(o);
    }

    @Transactional
    public Offer setImage(AppPrincipal principal, UUID offerId, String imageKey) {
        Offer o = owned(principal, offerId);
        o.setImageKey(imageKey);
        return offerRepository.save(o);
    }

    @Transactional
    public Offer submit(AppPrincipal principal, UUID offerId, TargetCommand proposed) {
        Offer o = ownedEditable(principal, offerId);
        if (o.getValidFrom() == null || o.getValidTo() == null || !o.getValidTo().isAfter(o.getValidFrom())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "a valid start and end date are required");
        }
        upsertTarget(o.getId(), proposed, principal.getUserId());
        o.setStatus(Offer.Status.PENDING_APPROVAL);
        o.setSubmittedAt(Instant.now());
        return offerRepository.save(o);
    }

    // ---- Super Admin control point ----------------------------------------

    @Transactional
    public Offer approve(AppPrincipal superAdmin, UUID offerId, TargetCommand override) {
        Offer o = offerRepository.findById(offerId).orElseThrow(() -> AppException.notFound("Offer"));
        if (o.getStatus() != Offer.Status.PENDING_APPROVAL) {
            throw new AppException(ErrorCode.CONFLICT, "Offer is not awaiting approval");
        }
        OfferTarget target = override != null
                ? upsertTarget(offerId, override, superAdmin.getUserId())
                : targetRepository.findByOfferId(offerId).orElseThrow(
                        () -> new AppException(ErrorCode.VALIDATION_FAILED, "Offer has no target audience"));

        o.setStatus(Offer.Status.ACTIVE);
        o.setValidatedByUserId(superAdmin.getUserId());
        o.setValidatedAt(Instant.now());
        offerRepository.save(o);

        List<UUID> recipients = targetingService.resolveRecipients(target);
        if (!recipients.isEmpty()) {
            String pct = o.getDiscountType() == Offer.DiscountType.PERCENTAGE
                    ? o.getDiscountValue().stripTrailingZeros().toPlainString() + "% off"
                    : "₹" + o.getDiscountValue().stripTrailingZeros().toPlainString() + " off";
            events.publishPromo("OFFER_PUBLISHED", o.getId(), o.getVendorCategoryId(), recipients,
                    o.getTitle(), pct + (o.getCouponCode() != null ? "  ·  code " + o.getCouponCode() : ""),
                    Map.of("offerId", o.getId().toString(), "type", "offer"));
        }
        return o;
    }

    @Transactional
    public Offer reject(AppPrincipal superAdmin, UUID offerId, String reason) {
        Offer o = offerRepository.findById(offerId).orElseThrow(() -> AppException.notFound("Offer"));
        if (o.getStatus() != Offer.Status.PENDING_APPROVAL) {
            throw new AppException(ErrorCode.CONFLICT, "Offer is not awaiting approval");
        }
        o.setStatus(Offer.Status.REJECTED);
        o.setRejectReason(reason);
        o.setValidatedByUserId(superAdmin.getUserId());
        o.setValidatedAt(Instant.now());
        return offerRepository.save(o);
    }

    // ---- reads ----------------------------------------------------------

    @Transactional(readOnly = true)
    public Offer require(UUID id) {
        return offerRepository.findById(id).orElseThrow(() -> AppException.notFound("Offer"));
    }

    @Transactional(readOnly = true)
    public OfferTarget targetOf(UUID offerId) {
        return targetRepository.findByOfferId(offerId).orElse(null);
    }

    @Transactional(readOnly = true)
    public Page<Offer> listMine(AppPrincipal principal, Pageable pageable) {
        if (principal.getRole() == Role.ADMIN) {
            return offerRepository.findByTenantIdOrderByCreatedAtDesc(principal.getTenantId(), pageable);
        }
        return offerRepository.findByCreatedByUserIdOrderByCreatedAtDesc(principal.getUserId(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Offer> listForSuperAdmin(Offer.Status status, Pageable pageable) {
        return status != null
                ? offerRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                : offerRepository.findByStatusOrderByCreatedAtDesc(Offer.Status.PENDING_APPROVAL, pageable);
    }

    /** Deals feed for a resident: ACTIVE + in validity + the resident is in the target audience. */
    @Transactional(readOnly = true)
    public List<Offer> feedForResident(AppPrincipal principal) {
        Instant now = Instant.now();
        return offerRepository.findByStatusOrderByCreatedAtDesc(Offer.Status.ACTIVE, Pageable.ofSize(200))
                .getContent().stream()
                .filter(o -> o.isLive(now))
                .filter(o -> isTargeted(o, principal))
                .sorted((a, b) -> Boolean.compare(isFeatured(b), isFeatured(a)))
                .toList();
    }

    private boolean isFeatured(Offer o) {
        return o.getServiceProviderId() != null && providerRepository.findById(o.getServiceProviderId())
                .map(p -> p.getTier() == com.singlepoint.provider.domain.ProviderTier.FEATURED).orElse(false);
    }

    private boolean isTargeted(Offer offer, AppPrincipal principal) {
        OfferTarget t = targetRepository.findByOfferId(offer.getId()).orElse(null);
        if (t == null) return false;
        UUID tenantId = principal.getTenantId();
        return switch (t.getTargetType()) {
            case ALL_TENANTS -> true;
            case SINGLE_TENANT, TENANT_LIST, USER_SEGMENT ->
                    tenantId != null && t.tenantIdList().contains(tenantId);
            case ENQUIRY_BASED -> t.getEnquiryCategoryId() != null
                    && ticketRepository.existsByRaisedByUserIdAndCategoryId(principal.getUserId(), t.getEnquiryCategoryId());
            case USER_LIST -> t.userIdList().contains(principal.getUserId());
        };
    }

    // ---- redemption ---------------------------------------------------

    @Transactional
    public OfferRedemption redeem(AppPrincipal principal, UUID offerId, String code) {
        // pessimistic lock so concurrent redemptions of the same offer can't race past the cap
        Offer o = offerRepository.findByIdForUpdate(offerId).orElseThrow(() -> AppException.notFound("Offer"));
        if (!o.isLive(Instant.now())) throw new AppException(ErrorCode.CONFLICT, "This offer is not currently active");
        if (o.getRedemptionLimitTotal() != null
                && redemptionRepository.countByOfferIdAndStatus(offerId, OfferRedemption.Status.REDEEMED) >= o.getRedemptionLimitTotal()) {
            throw new AppException(ErrorCode.OFFER_LIMIT_REACHED, "This offer has been fully redeemed");
        }
        long mine = redemptionRepository.countByOfferIdAndUserIdAndStatus(offerId, principal.getUserId(), OfferRedemption.Status.REDEEMED);
        if (mine >= o.getRedemptionLimitPerUser()) {
            throw new AppException(ErrorCode.OFFER_LIMIT_REACHED, "You've already redeemed this offer");
        }
        OfferRedemption r = new OfferRedemption();
        r.setOfferId(offerId);
        r.setUserId(principal.getUserId());
        r.setTenantId(principal.getTenantId());
        r.setCodeEntered(code);
        r.setVerifiedBy(OfferRedemption.VerifiedBy.RESIDENT);
        r.setRedeemedAt(Instant.now());
        OfferRedemption saved = redemptionRepository.save(r);
        metrics.offerRedeemed();
        return saved;
    }

    @Transactional
    public OfferRedemption confirmRedemption(AppPrincipal principal, UUID redemptionId) {
        OfferRedemption r = redemptionRepository.findById(redemptionId)
                .orElseThrow(() -> AppException.notFound("Redemption"));
        r.setVerifiedBy(principal.getRole() == Role.ADMIN
                ? OfferRedemption.VerifiedBy.ADMIN : OfferRedemption.VerifiedBy.PROVIDER);
        r.setConfirmedAt(Instant.now());
        return redemptionRepository.save(r);
    }

    // ---- feedback (MVP-8) -------------------------------------------

    public record Aggregates(Double ratingAvg, int ratingCount, Integer redemptionsRemaining) { }

    @Transactional(readOnly = true)
    public Aggregates aggregatesFor(Offer o) {
        List<Object[]> agg = feedbackRepository.ratingAggregate(o.getId());
        Object[] row = agg.isEmpty() ? new Object[]{null, 0L} : agg.get(0);
        Double avg = row[0] != null ? ((Number) row[0]).doubleValue() : null;
        int cnt = row[1] != null ? ((Number) row[1]).intValue() : 0;
        Integer remaining = null;
        if (o.getRedemptionLimitTotal() != null) {
            long used = redemptionRepository.countByOfferIdAndStatus(o.getId(), OfferRedemption.Status.REDEEMED);
            remaining = (int) Math.max(0, o.getRedemptionLimitTotal() - used);
        }
        return new Aggregates(avg, cnt, remaining);
    }

    @Transactional
    public com.singlepoint.offer.domain.OfferFeedback leaveFeedback(AppPrincipal principal, UUID offerId,
                                                                    int rating, String comment) {
        if (rating < 1 || rating > 5) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "rating must be 1-5");
        }
        Offer o = require(offerId);
        if (o.getStatus() != Offer.Status.ACTIVE && o.getStatus() != Offer.Status.EXPIRED) {
            throw new AppException(ErrorCode.CONFLICT, "This offer isn't open for feedback");
        }
        var f = feedbackRepository.findByOfferIdAndUserId(offerId, principal.getUserId())
                .orElseGet(com.singlepoint.offer.domain.OfferFeedback::new);
        f.setOfferId(offerId);
        f.setUserId(principal.getUserId());
        f.setRating(rating);
        f.setComment(comment != null && comment.isBlank() ? null : comment);
        f = feedbackRepository.save(f);
        if (o.getCreatedByUserId() != null) {
            events.publish("OFFER_FEEDBACK", "offer", offerId, o.getTenantId(),
                    List.of(o.getCreatedByUserId()), "New feedback on your offer",
                    rating + "★ — " + o.getTitle(), Map.of("offerId", offerId.toString(), "type", "offer"));
        }
        return f;
    }

    @Transactional(readOnly = true)
    public List<com.singlepoint.offer.domain.OfferFeedback> feedbackFor(AppPrincipal principal, UUID offerId) {
        Offer o = require(offerId);
        boolean ok = principal.getRole() == Role.SUPER_ADMIN
                || o.getCreatedByUserId().equals(principal.getUserId())
                || (principal.getRole() == Role.ADMIN && principal.getTenantId() != null
                    && principal.getTenantId().equals(o.getTenantId()));
        if (!ok) throw AppException.notFound("Offer");
        return feedbackRepository.findByOfferIdOrderByCreatedAtDesc(offerId);
    }

    // ---- housekeeping -------------------------------------------------

    @Transactional
    public int expireLapsedOffers() {
        List<Offer> lapsed = offerRepository.findByStatusAndValidToBefore(Offer.Status.ACTIVE, Instant.now());
        lapsed.forEach(o -> o.setStatus(Offer.Status.EXPIRED));
        offerRepository.saveAll(lapsed);
        return lapsed.size();
    }

    // ---- internals -------------------------------------------------

    private void apply(Offer o, OfferCommand c) {
        o.setVendorCategoryId(c.vendorCategoryId());
        o.setTitle(c.title());
        o.setDescription(c.description());
        o.setDiscountType(Offer.DiscountType.valueOf(c.discountType().toUpperCase()));
        o.setDiscountValue(c.discountValue());
        o.setCouponCode(c.couponCode());
        if (c.validFrom() != null) o.setValidFrom(c.validFrom());
        if (c.validTo() != null) o.setValidTo(c.validTo());
        if (c.redemptionLimitPerUser() != null && c.redemptionLimitPerUser() > 0) {
            o.setRedemptionLimitPerUser(c.redemptionLimitPerUser());
        }
        o.setRedemptionLimitTotal(c.redemptionLimitTotal());
        o.setTerms(c.terms());
    }

    private OfferTarget upsertTarget(UUID offerId, TargetCommand c, UUID setBy) {
        OfferTarget t = targetRepository.findByOfferId(offerId).orElseGet(OfferTarget::new);
        t.setOfferId(offerId);
        OfferTarget.TargetType type = OfferTarget.TargetType.valueOf(c.targetType().toUpperCase());
        t.setTargetType(type);
        t.setTenantIds(c.tenantIds() == null || c.tenantIds().isEmpty() ? null : String.join(",", c.tenantIds()));
        if (type == OfferTarget.TargetType.USER_LIST) {
            java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
            if (c.userIds() != null) ids.addAll(c.userIds());
            if (c.phones() != null) {
                for (String phone : c.phones()) {
                    userRepository.findByPhoneHash(crypto.lookupHash(
                            com.singlepoint.common.util.PhoneNumbers.normalize(phone)))
                        .ifPresent(u -> ids.add(u.getId().toString()));
                }
            }
            if (ids.isEmpty()) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "None of those phone numbers are registered");
            }
            t.setUserIds(String.join(",", ids));
        } else {
            t.setUserIds(null);
        }
        t.setEnquiryCategoryId(c.enquiryCategoryId() != null ? UUID.fromString(c.enquiryCategoryId()) : null);
        t.setSegmentFilter(c.segmentFilter());
        t.setSetByUserId(setBy);
        return targetRepository.save(t);
    }

    private Offer owned(AppPrincipal principal, UUID offerId) {
        Offer o = require(offerId);
        boolean ok = o.getCreatedByUserId().equals(principal.getUserId())
                || (principal.getRole() == Role.ADMIN && principal.getTenantId() != null
                    && principal.getTenantId().equals(o.getTenantId()));
        if (!ok) throw AppException.notFound("Offer");
        return o;
    }

    private Offer ownedEditable(AppPrincipal principal, UUID offerId) {
        Offer o = owned(principal, offerId);
        if (o.getStatus() != Offer.Status.DRAFT && o.getStatus() != Offer.Status.REJECTED) {
            throw new AppException(ErrorCode.CONFLICT, "Only draft or rejected offers can be edited");
        }
        return o;
    }
}
