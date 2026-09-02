package com.singlepoint.user;

import com.singlepoint.user.domain.MembershipStatus;
import com.singlepoint.user.domain.UserTenantMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserTenantMembershipRepository extends JpaRepository<UserTenantMembership, UUID> {

    List<UserTenantMembership> findByUserId(UUID userId);

    List<UserTenantMembership> findByUserIdAndStatus(UUID userId, MembershipStatus status);

    /** Deterministic order for picking a fallback active tenant / community list. */
    List<UserTenantMembership> findByUserIdAndStatusOrderByJoinedAtAscCreatedAtAsc(UUID userId, MembershipStatus status);

    Optional<UserTenantMembership> findByUserIdAndTenantIdAndStatus(UUID userId, UUID tenantId, MembershipStatus status);

    List<UserTenantMembership> findByUserIdAndTenantIdAndStatusIn(UUID userId, UUID tenantId,
                                                                 java.util.Collection<MembershipStatus> statuses);

    boolean existsByUserIdAndTenantIdAndStatusIn(UUID userId, UUID tenantId, List<MembershipStatus> statuses);

    List<UserTenantMembership> findByTenantIdAndStatusOrderByCreatedAtAsc(UUID tenantId, MembershipStatus status);

    List<UserTenantMembership> findByTenantIdAndStatus(UUID tenantId, MembershipStatus status);

    // ---- household (MVP-6) ------------------------------------------------

    List<UserTenantMembership> findByUserIdAndFlatIdAndStatusIn(UUID userId, UUID flatId,
                                                               java.util.Collection<MembershipStatus> statuses);

    List<UserTenantMembership> findByFlatIdAndStatusIn(UUID flatId,
                                                      java.util.Collection<MembershipStatus> statuses);
}
