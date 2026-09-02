package com.singlepoint.ticket.api;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class TicketDtos {

    private TicketDtos() { }

    public record RaiseRequest(@NotBlank String categoryId,
                               String subcategoryId,
                               @NotBlank @Size(max = 4000) String description,
                               String priority,
                               String serviceAddressText,
                               BigDecimal serviceGeoLat,
                               BigDecimal serviceGeoLng,
                               String serviceLandmark,
                               String preferredTimeWindow,
                               String flatId) { }

    public record RemarksRequest(String remarks) { }

    public record ResolveRequest(@NotBlank @Size(max = 4000) String resolutionNotes) { }

    public record AssignRequest(@NotBlank String providerId, String remarks) { }

    public record RejectRequest(String reason) { }

    public record ProviderStatusRequest(@NotBlank String toStatus, String reason, String resolutionNotes) { }

    public record CloseRequest(Integer rating, String remarks) { }

    public record PartyView(String name, String phone, String verificationStatus, String tier) { }

    public record TimelineEntry(String fromStatus, String toStatus, String actorRole, String actorName,
                                String remarks, Instant at) { }

    public record AttachmentView(UUID id, String url, String contentType, long sizeBytes,
                                 String originalFilename, Instant at) { }

    public record TicketView(UUID id, String referenceCode, String status, String requestMode,
                             String categoryId, String priority,
                             String description, String flatLabel,
                             String serviceAddressText, BigDecimal serviceGeoLat, BigDecimal serviceGeoLng,
                             String serviceLandmark, String preferredTimeWindow,
                             PartyView raisedBy, PartyView assignedProvider,
                             boolean allocationApprovedByResident,
                             Integer rating, String ratingComment, int reopenedCount,
                             String resolutionNotes, String holdReason,
                             Instant slaDueAt, Instant slaBreachedAt, Instant acknowledgedAt, Instant assignedAt,
                             Instant resolvedAt, Instant closedAt, Instant createdAt, Instant updatedAt) { }
}
