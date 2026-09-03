package com.singlepoint.user;

import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.flat.FlatRepository;
import com.singlepoint.flat.InviteCodeRepository;
import com.singlepoint.flat.InviteCodeService;
import com.singlepoint.flat.domain.Flat;
import com.singlepoint.flat.domain.InviteCode;
import com.singlepoint.security.TenantScopedExecutor;
import com.singlepoint.tenant.TenantRepository;
import com.singlepoint.tenant.domain.Tenant;
import com.singlepoint.tenant.domain.TenantStatus;
import com.singlepoint.user.domain.AppUser;
import com.singlepoint.user.domain.HouseholdRole;
import com.singlepoint.user.domain.MembershipRelation;
import com.singlepoint.user.domain.MembershipStatus;
import com.singlepoint.user.domain.Role;
import com.singlepoint.user.domain.UserTenantMembership;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MembershipService {

    private final UserTenantMembershipRepository membershipRepository;
    private final AppUserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private final InviteCodeService inviteCodeService;
    private final FlatRepository flatRepository;
    private final com.singlepoint.ticket.TicketRepository ticketRepository;
    private final com.singlepoint.payment.TicketPaymentRepository ticketPaymentRepository;
    private final TenantScopedExecutor tenantScoped;

    public MembershipService(UserTenantMembershipRepository membershipRepository,
                             AppUserRepository userRepository,
                             TenantRepository tenantRepository,
                             InviteCodeRepository inviteCodeRepository,
                             InviteCodeService inviteCodeService,
                             FlatRepository flatRepository,
                             com.singlepoint.ticket.TicketRepository ticketRepository,
                             com.singlepoint.payment.TicketPaymentRepository ticketPaymentRepository,
                             TenantScopedExecutor tenantScoped) {
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.inviteCodeRepository = inviteCodeRepository;
        this.inviteCodeService = inviteCodeService;
        this.flatRepository = flatRepository;
        this.ticketRepository = ticketRepository;
        this.ticketPaymentRepository = ticketPaymentRepository;
        this.tenantScoped = tenantScoped;
    }

    // ---- admin-initiated removal (MVP-7) --------------------------------

    public record OpenTicketRef(UUID ticketId, String referenceCode, String status) { }
    public record UnsettledPaymentRef(UUID ticketId, java.math.BigDecimal amount, String status) { }
    public record RemovalBlockers(boolean removable, List<OpenTicketRef> openTickets,
                                  List<UnsettledPaymentRef> unsettledPayments) { }

    /** What (if anything) blocks removing {@code userId} from {@code tenantId}. */
    @Transactional(readOnly = true)
    public RemovalBlockers removalBlockers(UUID tenantId, UUID userId) {
        List<OpenTicketRef> open = ticketRepository
                .findByTenantIdAndRaisedByUserIdAndStatusNot(tenantId, userId,
                        com.singlepoint.ticket.domain.TicketStatus.CLOSED)
                .stream().map(t -> new OpenTicketRef(t.getId(), t.getReferenceCode(), t.getStatus().name()))
                .toList();
        List<UnsettledPaymentRef> bills = ticketPaymentRepository.findUnsettledForRaiser(tenantId, userId,
                        List.of(com.singlepoint.payment.domain.TicketPayment.Status.PAID_ONLINE,
                                com.singlepoint.payment.domain.TicketPayment.Status.PAID_CASH,
                                com.singlepoint.payment.domain.TicketPayment.Status.WAIVED,
                                com.singlepoint.payment.domain.TicketPayment.Status.FAILED))
                .stream().map(p -> new UnsettledPaymentRef(p.getTicketId(), p.getAmount(), p.getStatus().name()))
                .toList();
        return new RemovalBlockers(open.isEmpty() && bills.isEmpty(), open, bills);
    }

    /** Admin / Super Admin removes a user from a community once the gate is clear. */
    @Transactional
    public List<UserTenantMembership> removeFromCommunity(UUID tenantId, UUID userId, UUID actingUserId) {
        RemovalBlockers blockers = removalBlockers(tenantId, userId);
        if (!blockers.removable()) {
            throw new AppException(ErrorCode.CONFLICT,
                    "This member has open tickets or unsettled bills in the community");
        }
        List<UserTenantMembership> active = membershipRepository.findByUserIdAndTenantIdAndStatusIn(
                userId, tenantId, List.of(MembershipStatus.ACTIVE));
        if (active.isEmpty()) throw AppException.notFound("Membership");
        for (UserTenantMembership m : active) {
            m.setStatus(MembershipStatus.EXITED);
            m.setExitedAt(Instant.now());
            m.setApprovedByUserId(actingUserId);
            if (m.getFlatId() != null) unlinkFromFlat(m.getFlatId(), userId);
            membershipRepository.save(m);
        }
        AppUser u = userRepository.findById(userId).orElse(null);
        if (u != null && tenantId.equals(u.getCurrentTenantId())) {
            UUID next = membershipRepository.findByUserIdAndStatusOrderByJoinedAtAscCreatedAtAsc(
                            userId, MembershipStatus.ACTIVE).stream()
                    .map(UserTenantMembership::getTenantId).findFirst().orElse(null);
            u.setCurrentTenantId(next);
            userRepository.save(u);
        }
        return active;
    }

    /** ACTIVE members of a community (for the admin roster). */
    @Transactional(readOnly = true)
    public List<UserTenantMembership> activeMembers(UUID tenantId) {
        return membershipRepository.findByTenantIdAndStatus(tenantId, MembershipStatus.ACTIVE);
    }

    // ---- household (MVP-6) ------------------------------------------------

    private static final List<MembershipStatus> LIVE = List.of(MembershipStatus.ACTIVE,
            MembershipStatus.PENDING_APPROVAL);

    private UserTenantMembership requireFlatMembership(UUID userId, UUID flatId) {
        return membershipRepository.findByUserIdAndFlatIdAndStatusIn(userId, flatId, List.of(MembershipStatus.ACTIVE))
                .stream().findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN, "That flat isn't one of yours"));
    }

    private UserTenantMembership requirePrimary(UUID userId, UUID flatId) {
        UserTenantMembership m = requireFlatMembership(userId, flatId);
        if (m.getHouseholdRole() != HouseholdRole.PRIMARY) {
            throw new AppException(ErrorCode.FORBIDDEN, "Only the primary member can manage this household");
        }
        return m;
    }

    /** The flats the caller is an ACTIVE member of in a given community. */
    @Transactional(readOnly = true)
    public List<UserTenantMembership> flatsForUserInTenant(UUID userId, UUID tenantId) {
        return membershipRepository.findByUserId(userId).stream()
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE
                        && m.getFlatId() != null && tenantId.equals(m.getTenantId()))
                .toList();
    }

    /** Roster of a flat — PRIMARY first. Caller must be a member of the flat. */
    @Transactional(readOnly = true)
    public List<UserTenantMembership> flatMembers(UUID callerUserId, UUID flatId) {
        requireFlatMembership(callerUserId, flatId);
        return membershipRepository.findByFlatIdAndStatusIn(flatId, LIVE).stream()
                .sorted((a, b) -> Boolean.compare(b.getHouseholdRole() == HouseholdRole.PRIMARY,
                        a.getHouseholdRole() == HouseholdRole.PRIMARY))
                .toList();
    }

    /** PRIMARY issues a household invite code for their flat. */
    @Transactional
    public InviteCode createHouseholdInvite(UUID primaryUserId, UUID flatId, Integer maxUses, Integer validDays) {
        UserTenantMembership m = requirePrimary(primaryUserId, flatId);
        return inviteCodeService.create(m.getTenantId(), primaryUserId, flatId,
                MembershipRelation.OCCUPANT, validDays, maxUses, InviteCode.Kind.HOUSEHOLD);
    }

    /** PRIMARY removes a SECONDARY member from their flat. */
    @Transactional
    public void removeFromFlat(UUID primaryUserId, UUID flatId, UUID targetUserId) {
        requirePrimary(primaryUserId, flatId);
        if (primaryUserId.equals(targetUserId)) {
            throw new AppException(ErrorCode.CONFLICT, "Use 'leave community' to remove yourself");
        }
        UserTenantMembership target = membershipRepository
                .findByUserIdAndFlatIdAndStatusIn(targetUserId, flatId, List.of(MembershipStatus.ACTIVE))
                .stream().findFirst()
                .orElseThrow(() -> AppException.notFound("Household member"));
        if (target.getHouseholdRole() == HouseholdRole.PRIMARY) {
            throw new AppException(ErrorCode.CONFLICT, "Can't remove another primary member");
        }
        target.setStatus(MembershipStatus.EXITED);
        target.setExitedAt(Instant.now());
        membershipRepository.save(target);
    }

    /** Resident joins a community — via invite code (auto-active) or as a pending approval request. */
    public UserTenantMembership join(UUID userId, UUID tenantId, String inviteCode, String requestedFlatLabel) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> AppException.notFound("Community"));
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new AppException(ErrorCode.CONFLICT, "This community is not accepting members right now");
        }

        return tenantScoped.inTenant(tenantId, () -> {
            UserTenantMembership m = new UserTenantMembership();
            m.setTenantId(tenantId);
            m.setUserId(userId);

            if (inviteCode != null && !inviteCode.isBlank()) {
                InviteCode code = inviteCodeRepository.findByCode(inviteCode.trim().toUpperCase())
                        .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST, "Invalid invite code"));
                if (!code.getTenantId().equals(tenantId)) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "This invite code is for a different community");
                }
                if (!code.isRedeemable()) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "This invite code is no longer valid");
                }
                // A flat code can be redeemed once per flat; a flat-less community code once per community.
                boolean dup = code.getFlatId() != null
                        ? !membershipRepository.findByUserIdAndFlatIdAndStatusIn(userId, code.getFlatId(),
                                List.of(MembershipStatus.ACTIVE, MembershipStatus.PENDING_APPROVAL)).isEmpty()
                        : membershipRepository.existsByUserIdAndTenantIdAndStatusIn(userId, tenantId,
                                List.of(MembershipStatus.ACTIVE, MembershipStatus.PENDING_APPROVAL));
                if (dup) {
                    throw new AppException(ErrorCode.CONFLICT, "You already have a membership here");
                }
                m.setRelation(code.getRelation());
                m.setStatus(MembershipStatus.ACTIVE);
                m.setJoinedAt(Instant.now());
                if (code.getKind() == InviteCode.Kind.HOUSEHOLD) {
                    m.setHouseholdRole(HouseholdRole.SECONDARY);
                    m.setInvitedByUserId(code.getCreatedByUserId());
                }
                if (code.getFlatId() != null) {
                    m.setFlatId(code.getFlatId());
                    linkOccupant(code.getFlatId(), userId, code.getRelation());
                }
                code.setUseCount(code.getUseCount() + 1);
                if (code.getUseCount() >= code.getMaxUses()) {
                    code.setStatus(InviteCode.Status.EXHAUSTED);
                }
                inviteCodeRepository.save(code);

                AppUser u = userRepository.findById(userId).orElseThrow();
                if (u.getCurrentTenantId() == null) {
                    u.setCurrentTenantId(tenantId);
                    userRepository.save(u);
                }
            } else {
                if (membershipRepository.existsByUserIdAndTenantIdAndStatusIn(userId, tenantId,
                        List.of(MembershipStatus.ACTIVE, MembershipStatus.PENDING_APPROVAL))) {
                    throw new AppException(ErrorCode.CONFLICT, "You already have a membership or pending request here");
                }
                m.setRelation(MembershipRelation.OCCUPANT);
                m.setStatus(MembershipStatus.PENDING_APPROVAL);
                m.setRequestedFlatLabel(requestedFlatLabel);
            }
            return membershipRepository.save(m);
        });
    }

    @Transactional(readOnly = true)
    public List<UserTenantMembership> pendingRequests(UUID tenantId) {
        return membershipRepository.findByTenantIdAndStatusOrderByCreatedAtAsc(
                tenantId, MembershipStatus.PENDING_APPROVAL);
    }

    @Transactional
    public UserTenantMembership approve(UUID tenantId, UUID membershipId, UUID adminUserId, UUID flatId) {
        UserTenantMembership m = membershipRepository.findById(membershipId)
                .filter(x -> x.getTenantId().equals(tenantId))
                .orElseThrow(() -> AppException.notFound("Join request"));
        if (m.getStatus() != MembershipStatus.PENDING_APPROVAL) {
            throw new AppException(ErrorCode.CONFLICT, "This request is not pending");
        }
        m.setStatus(MembershipStatus.ACTIVE);
        m.setApprovedByUserId(adminUserId);
        m.setJoinedAt(Instant.now());
        if (flatId != null) {
            Flat flat = flatRepository.findByIdAndTenantId(flatId, tenantId)
                    .orElseThrow(() -> AppException.notFound("Flat"));
            m.setFlatId(flat.getId());
            linkOccupant(flat.getId(), m.getUserId(), m.getRelation());
        }
        AppUser u = userRepository.findById(m.getUserId()).orElseThrow();
        if (u.getCurrentTenantId() == null) {
            u.setCurrentTenantId(tenantId);
            userRepository.save(u);
        }
        return membershipRepository.save(m);
    }

    /** Resident leaves a community — EXITs every ACTIVE membership they hold there and unlinks their flats. */
    @Transactional
    public List<UserTenantMembership> leave(UUID userId, UUID tenantId) {
        List<UserTenantMembership> active = membershipRepository.findByUserIdAndTenantIdAndStatusIn(
                userId, tenantId, List.of(MembershipStatus.ACTIVE));
        if (active.isEmpty()) throw AppException.notFound("Membership");
        for (UserTenantMembership m : active) {
            m.setStatus(MembershipStatus.EXITED);
            m.setExitedAt(Instant.now());
            if (m.getFlatId() != null) unlinkFromFlat(m.getFlatId(), userId);
            membershipRepository.save(m);
        }
        return active;
    }

    private void unlinkFromFlat(UUID flatId, UUID userId) {
        flatRepository.findById(flatId).ifPresent(flat -> {
            boolean changed = false;
            if (userId.equals(flat.getCurrentOccupantUserId())) {
                flat.setCurrentOccupantUserId(null);
                if (flat.getOccupancyType() == Flat.OccupancyType.RENTED) {
                    flat.setOccupancyType(Flat.OccupancyType.VACANT);
                }
                changed = true;
            }
            if (userId.equals(flat.getOwnerUserId())) {
                flat.setOwnerUserId(null);
                changed = true;
            }
            if (changed) flatRepository.save(flat);
        });
    }

    @Transactional
    public UserTenantMembership reject(UUID tenantId, UUID membershipId, UUID adminUserId) {
        UserTenantMembership m = membershipRepository.findById(membershipId)
                .filter(x -> x.getTenantId().equals(tenantId))
                .orElseThrow(() -> AppException.notFound("Join request"));
        if (m.getStatus() != MembershipStatus.PENDING_APPROVAL) {
            throw new AppException(ErrorCode.CONFLICT, "This request is not pending");
        }
        m.setStatus(MembershipStatus.REJECTED);
        m.setApprovedByUserId(adminUserId);
        return membershipRepository.save(m);
    }

    private void linkOccupant(UUID flatId, UUID userId, MembershipRelation relation) {
        flatRepository.findById(flatId).ifPresent(flat -> {
            if (relation == MembershipRelation.OCCUPANT && flat.getCurrentOccupantUserId() == null) {
                flat.setCurrentOccupantUserId(userId);
                if (flat.getOccupancyType() == Flat.OccupancyType.VACANT) {
                    flat.setOccupancyType(Flat.OccupancyType.RENTED);
                }
                flatRepository.save(flat);
            } else if (relation == MembershipRelation.OWNER && flat.getOwnerUserId() == null) {
                flat.setOwnerUserId(userId);
                flatRepository.save(flat);
            }
        });
    }
}
