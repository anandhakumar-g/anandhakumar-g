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
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/join-requests")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@Tag(name = "Admin — Join Requests", description = "Approve or reject residents joining the community")
public class AdminJoinRequestController {

    private final MembershipService membershipService;
    private final AppUserRepository userRepository;

    public AdminJoinRequestController(MembershipService membershipService, AppUserRepository userRepository) {
        this.membershipService = membershipService;
        this.userRepository = userRepository;
    }

    private UUID tenant(AppPrincipal p) {
        if (p.getTenantId() == null) throw new AppException(ErrorCode.FORBIDDEN, "No active community");
        return p.getTenantId();
    }

    public record JoinRequestView(UUID id, UUID userId, String userName, String userPhoneMasked,
                                  String requestedFlatLabel, String status, Instant createdAt) { }
    public record ApproveRequest(String flatId) { }

    @GetMapping
    public ResponseEntity<List<JoinRequestView>> pending(@AuthenticationPrincipal AppPrincipal p) {
        List<JoinRequestView> out = membershipService.pendingRequests(tenant(p)).stream().map(m -> {
            AppUser u = userRepository.findById(m.getUserId()).orElse(null);
            return new JoinRequestView(m.getId(), m.getUserId(),
                    u != null ? u.getName() : null,
                    u != null ? PhoneNumbers.mask(u.getPhone()) : null,
                    m.getRequestedFlatLabel(), m.getStatus().name(), m.getCreatedAt());
        }).toList();
        return ResponseEntity.ok(out);
    }

    @PostMapping("/{membershipId}/approve")
    public ResponseEntity<Void> approve(@AuthenticationPrincipal AppPrincipal p,
                                        @PathVariable UUID membershipId,
                                        @RequestBody(required = false) ApproveRequest body) {
        UUID flatId = (body != null && body.flatId() != null) ? UUID.fromString(body.flatId()) : null;
        membershipService.approve(tenant(p), membershipId, p.getUserId(), flatId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{membershipId}/reject")
    public ResponseEntity<Void> reject(@AuthenticationPrincipal AppPrincipal p, @PathVariable UUID membershipId) {
        membershipService.reject(tenant(p), membershipId, p.getUserId());
        return ResponseEntity.noContent().build();
    }
}
