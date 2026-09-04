package com.singlepoint.offer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.singlepoint.category.CategoryRepository;
import com.singlepoint.category.domain.Category;
import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.flat.FlatRepository;
import com.singlepoint.offer.domain.OfferTarget;
import com.singlepoint.tenant.TenantRepository;
import com.singlepoint.ticket.TicketRepository;
import com.singlepoint.user.UserTenantMembershipRepository;
import com.singlepoint.user.domain.MembershipStatus;
import com.singlepoint.user.domain.UserTenantMembership;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resolves an {@link OfferTarget} to the set of resident user ids that should receive the offer.
 * Reuses existing ticket / membership / flat data — no new tracking (blueprint 4.16).
 * Called at approval time under the Super Admin's (cross-tenant) DB scope.
 */
@Service
public class OfferTargetingService {

    private final UserTenantMembershipRepository membershipRepository;
    private final TenantRepository tenantRepository;
    private final TicketRepository ticketRepository;
    private final CategoryRepository categoryRepository;
    private final FlatRepository flatRepository;
    private final ObjectMapper mapper;

    public OfferTargetingService(UserTenantMembershipRepository membershipRepository,
                                 TenantRepository tenantRepository, TicketRepository ticketRepository,
                                 CategoryRepository categoryRepository, FlatRepository flatRepository,
                                 ObjectMapper mapper) {
        this.membershipRepository = membershipRepository;
        this.tenantRepository = tenantRepository;
        this.ticketRepository = ticketRepository;
        this.categoryRepository = categoryRepository;
        this.flatRepository = flatRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<UUID> resolveRecipients(OfferTarget target) {
        return switch (target.getTargetType()) {
            case ALL_TENANTS -> activeResidentsOf(tenantRepository.findAll().stream()
                    .map(t -> t.getId()).collect(Collectors.toList()));
            case SINGLE_TENANT, TENANT_LIST -> activeResidentsOf(target.tenantIdList());
            case ENQUIRY_BASED -> enquiryCohort(target);
            case USER_SEGMENT -> segment(target);
            // MVP-8: explicit people — may be community-less, so no membership filter.
            case USER_LIST -> target.userIdList();
        };
    }

    /** Human-readable summary of who an offer will reach — shown in the approval UI. */
    @Transactional(readOnly = true)
    public String describe(OfferTarget target) {
        return switch (target.getTargetType()) {
            case ALL_TENANTS -> "All communities";
            case SINGLE_TENANT -> "1 community";
            case TENANT_LIST -> target.tenantIdList().size() + " communities";
            case ENQUIRY_BASED -> "Residents who enquired about this category";
            case USER_SEGMENT -> "A resident segment";
            case USER_LIST -> target.userIdList().size() + " people";
        };
    }

    private List<UUID> activeResidentsOf(List<UUID> tenantIds) {
        Set<UUID> users = new LinkedHashSet<>();
        for (UUID tid : tenantIds) {
            for (UserTenantMembership m : membershipRepository.findByTenantIdAndStatus(tid, MembershipStatus.ACTIVE)) {
                users.add(m.getUserId());
            }
        }
        return List.copyOf(users);
    }

    private List<UUID> enquiryCohort(OfferTarget target) {
        if (target.getEnquiryCategoryId() == null) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "enquiry-based targeting needs an enquiry category");
        }
        Category cat = categoryRepository.findById(target.getEnquiryCategoryId())
                .orElseThrow(() -> AppException.notFound("Category"));
        if (cat.getRequestType() != Category.RequestType.ENQUIRY) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "the chosen category is not an enquiry category");
        }
        return ticketRepository.findDistinctRaiserIdsByCategoryId(cat.getId());
    }

    private List<UUID> segment(OfferTarget target) {
        List<UUID> base = activeResidentsOf(target.tenantIdList());
        if (target.getSegmentFilter() == null || target.getSegmentFilter().isBlank()) return base;
        try {
            JsonNode f = mapper.readTree(target.getSegmentFilter());
            String block = f.hasNonNull("block") ? f.get("block").asText() : null;
            if (block == null) return base;
            Set<UUID> inBlock = target.tenantIdList().stream()
                    .flatMap(tid -> flatRepository.findByTenantIdOrderByBlockAscFlatNumberAsc(tid).stream())
                    .filter(fl -> block.equalsIgnoreCase(fl.getBlock()) && fl.getCurrentOccupantUserId() != null)
                    .map(fl -> fl.getCurrentOccupantUserId())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return base.stream().filter(inBlock::contains).toList();
        } catch (Exception e) {
            return base;
        }
    }
}
