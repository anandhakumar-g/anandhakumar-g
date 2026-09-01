package com.singlepoint.auth.api;

import com.singlepoint.auth.AuthService;
import com.singlepoint.user.domain.AppUser;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() { }

    public record OtpRequest(@NotBlank String phone) { }

    public record OtpRequestResult(boolean sent, String devCode) { }

    public record OtpVerifyRequest(@NotBlank String phone,
                                   @NotBlank @Size(min = 4, max = 8) String code) { }

    public record ProfileRequest(@NotBlank @Size(max = 160) String name,
                                 @Email @Size(max = 200) String email) { }

    public record UserSummary(UUID id, String role, String name, String phoneMasked,
                              boolean profileCompleted, String preferredTheme, UUID activeTenantId) { }

    public record SessionResponse(String token, long expiresInSeconds, String onboardingState, UserSummary user) {

        public static SessionResponse from(AuthService.Session s) {
            AppUser u = s.user();
            String masked = com.singlepoint.common.util.PhoneNumbers.mask(u.getPhone());
            return new SessionResponse(
                    s.token(), s.expiresInSeconds(), s.onboardingState().name(),
                    new UserSummary(u.getId(), u.getRole().name(), u.getName(), masked,
                            u.isProfileCompleted(), u.getPreferredTheme(), s.activeTenantId()));
        }
    }
}
