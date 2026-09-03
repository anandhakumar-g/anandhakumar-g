package com.singlepoint.user.api;

import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.common.util.PhoneNumbers;
import com.singlepoint.security.AppPrincipal;
import com.singlepoint.user.AppUserRepository;
import com.singlepoint.user.MembershipService;
import com.singlepoint.user.domain.AppUser;
import com.singlepoint.user.domain.UserTenantMembership;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** MVP-7 (A4/I8): an admin removes a user from a community, gated on open tickets / unsettled bills. */
@RestController
@RequestMapping("/api/v1/admin/members")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@Tag(name = "Admin — Members", description = "Community roster and gated removal")
public class AdminMembershipController {

    private final MembershipService membershipService;
    private final AppUserRepository userRepository;

    public AdminMembershipController(MembershipService membershipService, AppUserRepository userRepository) {
        this.membershipService = membershipService;
        this.userRepository = userRepository;
    }

    private UUID tenant(AppPrincipal p) {
        if (p.getTenantId() == null) throw new AppException(ErrorCode.FORBIDDEN, "No active community");
        return p.getTenantId();
    }

    public record MemberView(UUID userId, String name, String phoneMasked, UUID flatId, String householdRole,
                             Instant joinedAt) { }

    @GetMapping
    @Operation(summary = "ACTIVE members of this community")
    @Transactional(readOnly = true)
    public ResponseEntity<List<MemberView>> list(@AuthenticationPrincipal AppPrincipal p) {
        List<UserTenantMembership> members = membershipService.activeMembers(tenant(p));
        Map<UUID, AppUser> users = userRepository.findAllById(
                        members.stream().map(UserTenantMembership::getUserId).toList())
                .stream().collect(Collectors.toMap(AppUser::getId, u -> u, (a, b) -> a));
        List<MemberView> out = members.stream().map(m -> {
            AppUser u = users.get(m.getUserId());
            return new MemberView(m.getUserId(), u != null ? u.getName() : null,
                    u != null ? PhoneNumbers.mask(u.getPhone()) : null, m.getFlatId(),
                    m.getHouseholdRole().name(), m.getJoinedAt());
        }).toList();
        return ResponseEntity.ok(out);
    }

    @GetMapping("/{userId}/removal-check")
    @Operation(summary = "Whether the member can be removed, and what blocks it")
    public ResponseEntity<MembershipService.RemovalBlockers> removalCheck(@AuthenticationPrincipal AppPrincipal p,
                                                                         @PathVariable UUID userId) {
        return ResponseEntity.ok(membershipService.removalBlockers(tenant(p), userId));
    }

    @PostMapping("/{userId}/remove")
    @Operation(summary = "Remove a member from this community (409 with the blockers if not clear)")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal AppPrincipal p, @PathVariable UUID userId) {
        membershipService.removeFromCommunity(tenant(p), userId, p.getUserId());
        return ResponseEntity.noContent().build();
    }
}
