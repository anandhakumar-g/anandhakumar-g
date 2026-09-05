package com.singlepoint.user.api;

import com.singlepoint.auth.AuthService;
import com.singlepoint.auth.api.AuthDtos;
import com.singlepoint.security.AppPrincipal;
import com.singlepoint.user.MembershipService;
import com.singlepoint.user.domain.UserTenantMembership;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/memberships")
@Tag(name = "Membership", description = "Joining a community")
public class MembershipController {

    private final MembershipService membershipService;
    private final AuthService authService;

    public MembershipController(MembershipService membershipService, AuthService authService) {
        this.membershipService = membershipService;
        this.authService = authService;
    }

    @PostMapping("/join")
    @Operation(summary = "Join a community by invite code, or submit a join request for admin approval")
    public ResponseEntity<AuthDtos.SessionResponse> join(@AuthenticationPrincipal AppPrincipal principal,
                                                         @Valid @RequestBody MeDtos.JoinRequest body) {
        UserTenantMembership m = membershipService.join(principal.getUserId(),
                UUID.fromString(body.tenantId()), body.inviteCode(), body.requestedFlatLabel());
        // Re-issue the session so the token carries the new (or still-pending) tenant scope.
        return ResponseEntity.ok(AuthDtos.SessionResponse.from(
                authService.refreshSessionFor(principal.getUserId(), principal.getDeviceId())));
    }
}
