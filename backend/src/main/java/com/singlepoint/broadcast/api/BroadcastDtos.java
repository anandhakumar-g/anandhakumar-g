package com.singlepoint.broadcast.api;

import com.singlepoint.broadcast.domain.Broadcast;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class BroadcastDtos {

    private BroadcastDtos() { }

    /** {@code scope} / {@code tenantId} are read only on the Super Admin route. */
    public record SendRequest(@NotBlank @Size(max = 160) String title,
                              @NotBlank @Size(max = 2000) String body,
                              String scope,
                              String tenantId) { }

    public record BroadcastView(UUID id, String scope, UUID tenantId, String tenantName,
                                UUID senderUserId, String senderName, String senderRole,
                                String title, String body, int recipientCount, Instant at) {

        public static BroadcastView of(Broadcast b, String senderName, String tenantName) {
            return new BroadcastView(b.getId(), b.getScope().name(), b.getTenantId(), tenantName,
                    b.getSenderUserId(), senderName, b.getSenderRole(),
                    b.getTitle(), b.getBody(), b.getRecipientCount(), b.getCreatedAt());
        }
    }
}
