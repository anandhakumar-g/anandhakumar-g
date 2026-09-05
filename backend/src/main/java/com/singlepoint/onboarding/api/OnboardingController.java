package com.singlepoint.onboarding.api;

import com.singlepoint.onboarding.OnboardingService;
import com.singlepoint.security.AppPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@Tag(name = "Community onboarding", description = "Request a new community and check its review status")
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping("/api/v1/onboarding/community")
    @Operation(summary = "Submit a community for Super Admin review")
    public ResponseEntity<OnboardingDtos.MyCommunityRequestView> submit(
            @AuthenticationPrincipal AppPrincipal principal,
            @Valid @RequestBody OnboardingDtos.CreateCommunityRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OnboardingDtos.MyCommunityRequestView.of(onboardingService.submit(principal, body)));
    }

    @GetMapping("/api/v1/me/community-request")
    @Operation(summary = "My latest community request and its status (null if none)")
    public ResponseEntity<OnboardingDtos.MyCommunityRequestView> mine(@AuthenticationPrincipal AppPrincipal principal) {
        return ResponseEntity.ok(onboardingService.myLatestRequest(principal.getUserId())
                .map(OnboardingDtos.MyCommunityRequestView::of).orElse(null));
    }
}
