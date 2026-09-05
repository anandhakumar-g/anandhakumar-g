package com.singlepoint.onboarding.api;

import com.singlepoint.tenant.domain.Tenant;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class OnboardingDtos {

    private OnboardingDtos() { }

    public record CreateCommunityRequest(@NotBlank @Size(max = 160) String name,
                                         @Size(max = 120) String city,
                                         @Size(max = 160) String locality,
                                         @Size(max = 400) String address,
                                         @Size(max = 12) String pincode) { }

    public record RejectRequest(@Size(max = 500) String reason) { }

    /** The caller's own latest request. */
    public record MyCommunityRequestView(UUID tenantId, String name, String status, Instant at) {
        public static MyCommunityRequestView of(Tenant t) {
            return new MyCommunityRequestView(t.getId(), t.getName(), t.getStatus().name(), t.getCreatedAt());
        }
    }

    /** One row of the Super Admin review queue, with the requester hydrated. */
    public record CommunityRequestView(UUID tenantId, String name, String city, String locality,
                                       String status, Instant requestedAt,
                                       UUID requestedByUserId, String requestedByName, String requestedByPhoneMasked) {
    }
}
