package com.singlepoint.ticket.api;

import com.singlepoint.common.util.PhoneNumbers;
import com.singlepoint.flat.FlatRepository;
import com.singlepoint.provider.ServiceProviderRepository;
import com.singlepoint.provider.domain.ServiceProvider;
import com.singlepoint.security.AppPrincipal;
import com.singlepoint.storage.StorageService;
import com.singlepoint.ticket.domain.Ticket;
import com.singlepoint.ticket.domain.TicketAttachment;
import com.singlepoint.ticket.domain.TicketStatus;
import com.singlepoint.ticket.domain.TicketStatusHistory;
import com.singlepoint.user.AppUserRepository;
import com.singlepoint.user.domain.AppUser;
import org.springframework.stereotype.Component;

@Component
public class TicketMapper {

    private final AppUserRepository userRepository;
    private final ServiceProviderRepository providerRepository;
    private final FlatRepository flatRepository;
    private final StorageService storageService;

    public TicketMapper(AppUserRepository userRepository, ServiceProviderRepository providerRepository,
                        FlatRepository flatRepository, StorageService storageService) {
        this.userRepository = userRepository;
        this.providerRepository = providerRepository;
        this.flatRepository = flatRepository;
        this.storageService = storageService;
    }

    public TicketDtos.TicketView toView(Ticket t, AppPrincipal viewer) {
        AppUser raiser = userRepository.findById(t.getRaisedByUserId()).orElse(null);
        boolean viewerIsAssignedProvider = viewer.isProvider()
                && providerRepository.findByUserId(viewer.getUserId())
                        .map(p -> p.getId().equals(t.getAssignedProviderId())).orElse(false);
        // A provider legitimately needs contact details from assignment until the ticket closes.
        boolean engaged = t.getAssignedProviderId() != null && t.getStatus() != TicketStatus.CLOSED;

        boolean revealRaiserPhone = viewer.isResident() && t.getRaisedByUserId().equals(viewer.getUserId())
                || (viewerIsAssignedProvider && engaged);
        TicketDtos.PartyView raisedBy = raiser == null ? null : new TicketDtos.PartyView(
                raiser.getName(),
                revealRaiserPhone ? raiser.getPhone() : PhoneNumbers.mask(raiser.getPhone()),
                null, null, null, null, null, raiser.getAwayUntil());

        TicketDtos.PartyView provider = null;
        if (t.getAssignedProviderId() != null) {
            ServiceProvider p = providerRepository.findById(t.getAssignedProviderId()).orElse(null);
            if (p != null) {
                boolean revealProviderPhone = viewer.isAdmin()
                        || (viewer.isResident() && t.getRaisedByUserId().equals(viewer.getUserId()) && engaged)
                        || viewerIsAssignedProvider;
                provider = new TicketDtos.PartyView(
                        p.getName(),
                        revealProviderPhone ? p.getContactPhone() : PhoneNumbers.mask(p.getContactPhone()),
                        p.getVerificationStatus().name(), p.getTier().name(),
                        p.getRatingAvg(), p.getRatingCount(),
                        p.getAvailability().name(), null);
            }
        }

        String flatLabel = t.getFlatId() == null ? null :
                flatRepository.findById(t.getFlatId()).map(f -> f.label()).orElse(null);

        // Full service address only for the raiser, the community admin, or the assigned
        // provider while the job is live. Everyone else sees the landmark only.
        boolean revealAddress = viewer.isAdmin()
                || (viewer.isResident() && t.getRaisedByUserId().equals(viewer.getUserId()))
                || (viewerIsAssignedProvider && engaged);
        String serviceAddress = revealAddress ? t.getServiceAddressText() : null;
        java.math.BigDecimal geoLat = revealAddress ? t.getServiceGeoLat() : null;
        java.math.BigDecimal geoLng = revealAddress ? t.getServiceGeoLng() : null;

        return new TicketDtos.TicketView(
                t.getId(), t.getReferenceCode(), t.getStatus().name(), t.getRequestMode().name(),
                t.getCategoryId().toString(),
                t.getPriority() != null ? t.getPriority().name() : null,
                t.getDescription(), flatLabel,
                serviceAddress, geoLat, geoLng,
                t.getServiceLandmark(), t.getPreferredTimeWindow(),
                raisedBy, provider, t.isAllocationApprovedByResident(),
                t.getRating(), t.getRatingComment(), t.getReopenedCount(),
                t.getResolutionNotes(), t.getHoldReason(),
                t.getSlaDueAt(), t.getSlaBreachedAt(), t.getAcknowledgedAt(), t.getAssignedAt(),
                t.getResolvedAt(), t.getClosedAt(), t.getCreatedAt(), t.getUpdatedAt());
    }

    public TicketDtos.TimelineEntry toTimeline(TicketStatusHistory h) {
        String actorName = h.getChangedByUserId() == null ? null
                : userRepository.findById(h.getChangedByUserId()).map(AppUser::getName).orElse(null);
        return new TicketDtos.TimelineEntry(
                h.getFromStatus() != null ? h.getFromStatus().name() : null,
                h.getToStatus().name(), h.getActorRole(), actorName, h.getRemarks(), h.getCreatedAt());
    }

    public TicketDtos.AttachmentView toAttachment(TicketAttachment a) {
        return new TicketDtos.AttachmentView(a.getId(), storageService.publicUrl(a.getStorageKey()),
                a.getContentType(), a.getSizeBytes(), a.getOriginalFilename(), a.getCreatedAt());
    }
}
