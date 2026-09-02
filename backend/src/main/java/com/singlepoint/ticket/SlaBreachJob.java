package com.singlepoint.ticket;

import com.singlepoint.notification.DomainEventPublisher;
import com.singlepoint.provider.ServiceProviderRepository;
import com.singlepoint.security.TenantScopedExecutor;
import com.singlepoint.ticket.domain.Ticket;
import com.singlepoint.ticket.domain.TicketStatus;
import com.singlepoint.user.AppUserRepository;
import com.singlepoint.user.domain.AppUser;
import com.singlepoint.user.domain.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MVP-5 (A2): flags an open ticket the first time it is found past its {@code sla_due_at}
 * and alerts the community admins + the assigned provider. Alert-and-flag only — the ticket
 * is not moved, re-prioritised or rerouted.
 */
@Component
public class SlaBreachJob {

    private static final Logger log = LoggerFactory.getLogger(SlaBreachJob.class);
    private static final List<TicketStatus> CLOSED_STATES = List.of(TicketStatus.RESOLVED, TicketStatus.CLOSED);

    private final TicketRepository ticketRepository;
    private final AppUserRepository userRepository;
    private final ServiceProviderRepository providerRepository;
    private final DomainEventPublisher events;
    private final TenantScopedExecutor tenantScoped;
    private final boolean enabled;

    public SlaBreachJob(TicketRepository ticketRepository, AppUserRepository userRepository,
                        ServiceProviderRepository providerRepository, DomainEventPublisher events,
                        TenantScopedExecutor tenantScoped,
                        @Value("${sp.ticket.sla.enabled:true}") boolean enabled) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.providerRepository = providerRepository;
        this.events = events;
        this.tenantScoped = tenantScoped;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${sp.ticket.sla.cron:0 5 * * * *}")
    public void run() {
        if (enabled) runNow();
    }

    /** Runs the scan regardless of the {@code sp.ticket.sla.enabled} flag. Used by tests. */
    public int runNow() {
        int[] flaggedBox = {0};
        tenantScoped.inWildcard(() -> {
            List<Ticket> candidates = ticketRepository.findSlaBreachCandidates(Instant.now(), CLOSED_STATES);
            int flagged = 0;
            for (Ticket t : candidates) {
                t.setSlaBreachedAt(Instant.now());
                ticketRepository.save(t);

                List<UUID> recipients = new ArrayList<>(
                        userRepository.findByRoleAndCurrentTenantId(Role.ADMIN, t.getTenantId())
                                .stream().map(AppUser::getId).collect(Collectors.toList()));
                if (t.getAssignedProviderId() != null) {
                    providerRepository.findById(t.getAssignedProviderId())
                            .map(p -> p.getUserId())
                            .ifPresent(uid -> { if (uid != null) recipients.add(uid); });
                }
                if (!recipients.isEmpty()) {
                    events.publish("TICKET_SLA_BREACHED", "ticket", t.getId(), t.getTenantId(), recipients,
                            "SLA breached — ticket " + t.getReferenceCode(),
                            "This ticket has passed its response deadline and still needs attention.",
                            Map.of("ticketId", t.getId().toString(), "reference", t.getReferenceCode(),
                                    "status", t.getStatus().name()));
                }
                flagged++;
            }
            if (flagged > 0) log.info("Flagged {} ticket(s) as SLA-breached", flagged);
            flaggedBox[0] = flagged;
        });
        return flaggedBox[0];
    }
}
