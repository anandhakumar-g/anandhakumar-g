package com.singlepoint.user;

import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.flat.FlatRepository;
import com.singlepoint.flat.InviteCodeRepository;
import com.singlepoint.flat.domain.Flat;
import com.singlepoint.flat.domain.InviteCode;
import com.singlepoint.security.TenantScopedExecutor;
import com.singlepoint.tenant.TenantRepository;
import com.singlepoint.tenant.domain.Tenant;
import com.singlepoint.tenant.domain.TenantStatus;
import com.singlepoint.user.domain.AppUser;
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
    private final FlatRepository flatRepository;
    private final TenantScopedExecutor tenantScoped;

    public MembershipService(UserTenantMembershipRepository membershipRepository,
                             AppUserRepository userRepository,
                             TenantRepository tenantRepository,
                             InviteCodeRepository inviteCodeRepository,
                             FlatRepository flatRepository,
                             TenantScopedExecutor tenantScoped) {
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.inviteCodeRepository = inviteCodeRepository;
        this.flatRepository = flatRepository;
        this.tenantScoped = tenantScoped;
    }

    /** Resident joins a community — via invite code (auto-active) or as a pending approval request. */
    public UserTenantMembership join(UUID userId, UUID tenantId, String inviteCode, String requestedFlatLabel) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> AppException.notFound("Community"));
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new AppException(ErrorCode.CONFLICT, "This community is not accepting members right now");
        }
        if (membershipRepository.existsByUserIdAndTenantIdAndStatusIn(userId, tenantId,
                List.of(MembershipStatus.ACTIVE, MembershipStatus.PENDING_APPROVAL))) {
            throw new AppException(ErrorCode.CONFLICT, "You already have a membership or pending request here");
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
                m.setRelation(code.getRelation());
                m.setStatus(MembershipStatus.ACTIVE);
                m.setJoinedAt(Instant.now());
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
