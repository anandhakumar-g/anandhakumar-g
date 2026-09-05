package com.singlepoint.onboarding.api;

import com.singlepoint.common.dto.PageResponse;
import com.singlepoint.onboarding.OnboardingService;
import com.singlepoint.tenant.domain.TenantStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/superadmin/community-requests")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Super Admin — Community requests", description = "Review self-onboarded communities")
public class SuperAdminCommunityRequestController {

    private final OnboardingService onboardingService;

    public SuperAdminCommunityRequestController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @GetMapping
    @Operation(summary = "The review queue (default: PENDING_REVIEW)")
    public ResponseEntity<PageResponse<OnboardingDtos.CommunityRequestView>> queue(
            @RequestParam(defaultValue = "PENDING_REVIEW") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        TenantStatus st = TenantStatus.valueOf(status.trim().toUpperCase());
        var result = onboardingService.queue(st, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        return ResponseEntity.ok(PageResponse.of(result, v -> v));
    }

    @PostMapping("/{tenantId}/approve")
    @Operation(summary = "Approve — tenant goes ACTIVE, the requester becomes its admin")
    public ResponseEntity<OnboardingDtos.MyCommunityRequestView> approve(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(OnboardingDtos.MyCommunityRequestView.of(onboardingService.approve(tenantId)));
    }

    @PostMapping("/{tenantId}/reject")
    @Operation(summary = "Reject — tenant is archived, the requester is notified with the reason")
    public ResponseEntity<OnboardingDtos.MyCommunityRequestView> reject(
            @PathVariable UUID tenantId, @RequestBody(required = false) OnboardingDtos.RejectRequest body) {
        String reason = body == null ? null : body.reason();
        return ResponseEntity.ok(OnboardingDtos.MyCommunityRequestView.of(onboardingService.reject(tenantId, reason)));
    }
}
