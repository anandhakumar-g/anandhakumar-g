package com.singlepoint.auth.api;

import com.singlepoint.auth.AuthService;
import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.security.AppPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Phone + OTP login and profile completion")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/otp/request")
    @Operation(summary = "Request a login OTP for a phone number")
    public ResponseEntity<AuthDtos.OtpRequestResult> requestOtp(@Valid @RequestBody AuthDtos.OtpRequest body) {
        String devCode = authService.requestOtp(body.phone());
        return ResponseEntity.ok(new AuthDtos.OtpRequestResult(true, devCode));
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Verify an OTP and receive a session token")
    public ResponseEntity<AuthDtos.SessionResponse> verifyOtp(@Valid @RequestBody AuthDtos.OtpVerifyRequest body) {
        return ResponseEntity.ok(AuthDtos.SessionResponse.from(authService.verifyOtp(body.phone(), body.code())));
    }

    @PostMapping("/profile")
    @Operation(summary = "Complete or update the signed-in user's profile; returns a fresh session")
    public ResponseEntity<AuthDtos.SessionResponse> completeProfile(
            @AuthenticationPrincipal AppPrincipal principal,
            @Valid @RequestBody AuthDtos.ProfileRequest body) {
        authService.completeProfileAndRefresh(principal.getUserId(), body.name(), body.email());
        return ResponseEntity.ok(AuthDtos.SessionResponse.from(
                authService.refreshSessionFor(principal.getUserId())));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Re-mint the caller's session (picks up an approved membership / a community switch)")
    public ResponseEntity<AuthDtos.SessionResponse> refresh(@AuthenticationPrincipal AppPrincipal principal) {
        if (principal == null) throw new AppException(ErrorCode.UNAUTHENTICATED, "Sign in first");
        return ResponseEntity.ok(AuthDtos.SessionResponse.from(
                authService.refreshSessionFor(principal.getUserId())));
    }
}
