package com.singlepoint.notification;

import com.singlepoint.notification.domain.NotificationPreference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository repository;

    public NotificationPreferenceService(NotificationPreferenceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public NotificationPreference getOrCreate(UUID userId) {
        return repository.findByUserId(userId).orElseGet(() -> {
            NotificationPreference p = new NotificationPreference();
            p.setUserId(userId);
            return repository.save(p);
        });
    }

    @Transactional
    public NotificationPreference update(UUID userId, Set<String> subscribedCategoryIds, Integer freqCap,
                                        NotificationPreference.DigestMode digestMode,
                                        Boolean ticketEnabled, Boolean promoEnabled) {
        NotificationPreference p = getOrCreate(userId);
        if (subscribedCategoryIds != null) p.setSubscribedCategorySet(subscribedCategoryIds);
        if (freqCap != null && freqCap >= 0) p.setPromoFrequencyCapPerWeek(freqCap);
        if (digestMode != null) p.setDigestMode(digestMode);
        if (ticketEnabled != null) p.setTicketNotificationsEnabled(ticketEnabled);
        if (promoEnabled != null) p.setPromoNotificationsEnabled(promoEnabled);
        return repository.save(p);
    }
}
