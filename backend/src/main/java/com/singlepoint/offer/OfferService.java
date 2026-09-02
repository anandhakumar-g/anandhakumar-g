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
    private final OfferTargetingService targetingService;
    private final VendorCategoryRepository vendorCategoryRepository;
    private final ServiceProviderRepository providerRepository;
    private final TicketRepository ticketRepository;
    private final DomainEventPublisher events;

    public OfferService(OfferRepository offerRepository, OfferTargetRepository targetRepository,
                        OfferRedemptionRepository redemptionRepository, OfferTargetingService targetingService,
                        VendorCategoryRepository vendorCategoryRepository, ServiceProviderRepository providerRepository,
                        TicketRepository ticketRepository, DomainEventPublisher events) {
        this.offerRepository = offerRepository;
        this.targetRepository = targetRepository;
        this.redemptionRepository = redemptionRepository;
        this.targetingService = targetingService;
        this.vendorCategoryRepository = vendorCategoryRepository;
        this.providerRepository = providerRepository;
        this.ticketRepository = ticketRepository;
        this.events = events;
    }

    public record OfferCommand(UUID vendorCategoryId, String title, String description,
                               String discountType, BigDecimal discountValue, String couponCode,
                               Instant validFrom, Instant validTo, Integer redemptionLimitPerUser,
                               Integer redemptionLimitTotal, String terms) { }

    public record TargetCommand(String targetType, List<String> tenantIds,
                                String enquiryCategoryId, String segmentFilter) { }

    // ---- authoring -----------------------------------------------------------

    @Transactional
    public Offer createDraft(AppPrincipal principal, OfferCommand cmd) {
        vendorCategoryRepository.findById(cmd.vendorCategoryId())
                .orElseThrow(() -> AppException.notFound("Vendor category"));
        Offer o = new Offer();
        o.setCreatedByUserId(principal.getUserId());
        o.setCreatedByRole(principal.getRole().name());
        if (principal.getRole() == Role.PROVIDER) {
            ServiceProvider sp = providerRepository.findByUserId(principal.getUserId())
                    .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN, "No provider profile"));
            o.setServiceProviderId(sp.getId());
        } else if (principal.getRole() == Role.ADMIN) {
            o.setTenantId(principal.getTenantId());
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
                .toList();
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
        };
    }

    // ---- redemption ---------------------------------------------------

    @Transactional
    public OfferRedemption redeem(AppPrincipal principal, UUID offerId, String code) {
        Offer o = require(offerId);
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
        return redemptionRepository.save(r);
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
        t.setTargetType(OfferTarget.TargetType.valueOf(c.targetType().toUpperCase()));
        t.setTenantIds(c.tenantIds() == null ? null : String.join(",", c.tenantIds()));
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
