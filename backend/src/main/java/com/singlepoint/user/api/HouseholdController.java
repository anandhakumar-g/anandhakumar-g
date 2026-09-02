package com.singlepoint.user.api;

import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.common.util.PhoneNumbers;
import com.singlepoint.flat.FlatRepository;
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
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** MVP-6 (C): a resident manages the members of the flat(s) they belong to. */
@RestController
@RequestMapping("/api/v1/me")
@PreAuthorize("hasRole('RESIDENT')")
@Tag(name = "Me — Household", description = "Flats I belong to and the family members in them")
public class HouseholdController {

    private final MembershipService membershipService;
    private final FlatRepository flatRepository;
    private final AppUserRepository userRepository;

    public HouseholdController(MembershipService membershipService, FlatRepository flatRepository,
                              AppUserRepository userRepository) {
        this.membershipService = membershipService;
        this.flatRepository = flatRepository;
        this.userRepository = userRepository;
    }

    private UUID tenant(AppPrincipal p) {
        if (p.getTenantId() == null) throw new AppException(ErrorCode.FORBIDDEN, "Join a community first");
        return p.getTenantId();
    }

    public record MyFlat(UUID flatId, String label, String householdRole) { }
    public record HouseholdMember(UUID userId, String name, String phoneMasked, String householdRole,
                                  String status, Instant joinedAt) { }
    public record HouseholdInviteRequest(String flatId, Integer maxUses, Integer validDays) { }
    public record InviteCodeView(String code, int maxUses, Instant expiresAt) { }

    @GetMapping("/flats")
    @Operation(summary = "The flats I belong to in my active community")
    public ResponseEntity<List<MyFlat>> myFlats(@AuthenticationPrincipal AppPrincipal p) {
        List<MyFlat> out = membershipService.flatsForUserInTenant(p.getUserId(), tenant(p)).stream()
                .map(m -> new MyFlat(m.getFlatId(),
                        flatRepository.findById(m.getFlatId()).map(f -> f.label()).orElse(null),
                        m.getHouseholdRole().name()))
                .toList();
        return ResponseEntity.ok(out);
    }

    @GetMapping("/household/{flatId}/members")
    @Operation(summary = "Everyone in a flat (primary first)")
    public ResponseEntity<List<HouseholdMember>> members(@AuthenticationPrincipal AppPrincipal p,
                                                         @PathVariable UUID flatId) {
        List<HouseholdMember> out = membershipService.flatMembers(p.getUserId(), flatId).stream()
                .map(m -> {
                    AppUser u = userRepository.findById(m.getUserId()).orElse(null);
                    return new HouseholdMember(m.getUserId(),
                            u != null ? u.getName() : null,
                            u != null ? PhoneNumbers.mask(u.getPhone()) : null,
                            m.getHouseholdRole().name(), m.getStatus().name(), m.getJoinedAt());
                })
                .toList();
        return ResponseEntity.ok(out);
    }

    @PostMapping("/household/invites")
    @Operation(summary = "Primary member: generate a code that adds a family member to a flat as SECONDARY")
    public ResponseEntity<InviteCodeView> createInvite(@AuthenticationPrincipal AppPrincipal p,
                                                       @RequestBody HouseholdInviteRequest body) {
        if (body.flatId() == null || body.flatId().isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "flatId is required");
        }
        var code = membershipService.createHouseholdInvite(p.getUserId(),
                UUID.fromString(body.flatId()), body.maxUses(), body.validDays());
        return ResponseEntity.ok(new InviteCodeView(code.getCode(), code.getMaxUses(), code.getExpiresAt()));
    }

    @PostMapping("/household/{flatId}/members/{userId}/remove")
    @Operation(summary = "Primary member: remove a family member from a flat")
    public ResponseEntity<List<HouseholdMember>> remove(@AuthenticationPrincipal AppPrincipal p,
                                                        @PathVariable UUID flatId, @PathVariable UUID userId) {
        membershipService.removeFromFlat(p.getUserId(), flatId, userId);
        return members(p, flatId);
    }
}
