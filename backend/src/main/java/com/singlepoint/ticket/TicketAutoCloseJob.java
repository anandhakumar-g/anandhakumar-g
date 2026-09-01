package com.singlepoint.ticket;

import com.singlepoint.notification.DomainEventPublisher;
import com.singlepoint.security.TenantScopedExecutor;
import com.singlepoint.tenant.TenantRepository;
import com.singlepoint.ticket.domain.Ticket;
import com.singlepoint.ticket.domain.TicketStatus;
import com.singlepoint.ticket.domain.TicketStatusHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/** Closes RESOLVED tickets once the tenant's reopen window has elapsed (ADR: open decision #4). */
@Component
public class TicketAutoCloseJob {

    private static final Logger log = LoggerFactory.getLogger(TicketAutoCloseJob.class);

    private final TicketRepository ticketRepository;
    private final TicketStatusHistoryRepository historyRepository;
    private final TenantRepository tenantRepository;
    private final DomainEventPublisher events;
    private final TenantScopedExecutor tenantScoped;
    private final boolean enabled;
    private final int fallbackWindowHours;

    public TicketAutoCloseJob(TicketRepository ticketRepository, TicketStatusHistoryRepository historyRepository,
                              TenantRepository tenantRepository, DomainEventPublisher events,
                              TenantScopedExecutor tenantScoped,
                              @Value("${sp.ticket.auto-close.enabled:true}") boolean enabled,
                              @Value("${sp.ticket.default-reopen-window-hours:72}") int fallbackWindowHours) {
        this.ticketRepository = ticketRepository;
        this.historyRepository = historyRepository;
        this.tenantRepository = tenantRepository;
        this.events = events;
        this.tenantScoped = tenantScoped;
        this.enabled = enabled;
        this.fallbackWindowHours = fallbackWindowHours;
    }

    @Scheduled(cron = "${sp.ticket.auto-close.cron:0 15 * * * *}")
    public void run() {
        if (!enabled) return;
        tenantScoped.inWildcard(() -> {
            List<Ticket> candidates = ticketRepository.findByStatusAndResolvedAtBefore(
                    TicketStatus.RESOLVED, Instant.now().minus(1, ChronoUnit.HOURS));
            int closed = 0;
            for (Ticket t : candidates) {
                int window = tenantRepository.findById(t.getTenantId())
                        .map(x -> x.getReopenWindowHours()).orElse(fallbackWindowHours);
                if (t.getResolvedAt() == null
                        || t.getResolvedAt().plus(window, ChronoUnit.HOURS).isAfter(Instant.now())) {
                    continue;
                }
                t.setStatus(TicketStatus.CLOSED);
                t.setClosedAt(Instant.now());
                ticketRepository.save(t);

                TicketStatusHistory h = new TicketStatusHistory();
                h.setTenantId(t.getTenantId());
                h.setTicketId(t.getId());
                h.setFromStatus(TicketStatus.RESOLVED);
                h.setToStatus(TicketStatus.CLOSED);
                h.setActorRole("SYSTEM");
                h.setRemarks("Auto-closed after the " + window + "h reopen window");
                historyRepository.save(h);

                events.publish("TICKET_CLOSED", "ticket", t.getId(), t.getTenantId(),
                        List.of(t.getRaisedByUserId()),
                        "Ticket " + t.getReferenceCode() + " closed",
                        "Automatically closed after the reopen window.",
                        Map.of("ticketId", t.getId().toString(), "reference", t.getReferenceCode()));
                closed++;
            }
            if (closed > 0) log.info("Auto-closed {} resolved ticket(s)", closed);
        });
    }
}
