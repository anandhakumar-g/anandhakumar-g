package com.singlepoint.user.api;

import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public final class MeDtos {

    private MeDtos() { }

    public record MembershipView(UUID tenantId, String tenantName, String status, String relation,
                                 UUID flatId, String flatLabel) { }

    public record TenantBranding(UUID tenantId, String name, String logoUrl,
                                 String defaultTheme, String brandPrimaryColor) { }

    public record MeResponse(UUID userId, String role, String name, String phoneMasked, String email,
                             boolean profileCompleted, String preferredTheme, UUID activeTenantId,
                             TenantBranding activeTenantBranding, List<MembershipView> memberships) { }

    public record ThemeRequest(@NotBlank String theme) { }

    public record JoinRequest(@NotBlank String tenantId, String inviteCode, String requestedFlatLabel) { }

    public record DeviceRequest(@NotBlank String token, @NotBlank String platform, String provider) { }
}
