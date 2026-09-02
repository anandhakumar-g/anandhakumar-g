package com.singlepoint.ticket;

import com.singlepoint.common.error.AppException;
import com.singlepoint.ticket.domain.TicketStatus;
import com.singlepoint.user.domain.Role;
import org.springframework.stereotype.Component;

import java.util.Set;

import static com.singlepoint.ticket.domain.TicketStatus.*;

/** Single source of truth for legal ticket transitions and who may perform them. */
@Component
public class TicketStateMachine {

    /** actor is null for SYSTEM (scheduled auto-close). */
    private record Edge(TicketStatus from, TicketStatus to, Role actor) { }

    private static final Set<Edge> EDGES = Set.of(
            // Admin triage
            new Edge(NEW, ACKNOWLEDGED, Role.ADMIN),
            new Edge(NEW, ASSIGNED, Role.ADMIN),
            new Edge(NEW, RESOLVED, Role.ADMIN),
            new Edge(ACKNOWLEDGED, ASSIGNED, Role.ADMIN),
            new Edge(ACKNOWLEDGED, RESOLVED, Role.ADMIN),
            // Admin parks for resident approval-of-allocation (only reached when
            // the tenant gate is ON). Reachable from triage and from any live
            // assignment state (admin re-picks a helper before the resident answers).
            new Edge(NEW, PENDING_RESIDENT_APPROVAL, Role.ADMIN),
            new Edge(ACKNOWLEDGED, PENDING_RESIDENT_APPROVAL, Role.ADMIN),
            new Edge(PENDING_RESIDENT_APPROVAL, PENDING_RESIDENT_APPROVAL, Role.ADMIN),
            new Edge(ASSIGNED, PENDING_RESIDENT_APPROVAL, Role.ADMIN),
            new Edge(ACCEPTED, PENDING_RESIDENT_APPROVAL, Role.ADMIN),
            new Edge(IN_PROGRESS, PENDING_RESIDENT_APPROVAL, Role.ADMIN),
            new Edge(ON_HOLD, PENDING_RESIDENT_APPROVAL, Role.ADMIN),
            new Edge(REJECTED, PENDING_RESIDENT_APPROVAL, Role.ADMIN),
            new Edge(REOPENED, PENDING_RESIDENT_APPROVAL, Role.ADMIN),
            // Admin override: push a parked ticket straight through (e.g. the gate
            // was turned off after the ticket parked).
            new Edge(PENDING_RESIDENT_APPROVAL, ASSIGNED, Role.ADMIN),
            // Admin reroute (-> ASSIGNED) from any live assignment state
            new Edge(ASSIGNED, ASSIGNED, Role.ADMIN),
            new Edge(REJECTED, ASSIGNED, Role.ADMIN),
            new Edge(ACCEPTED, ASSIGNED, Role.ADMIN),
            new Edge(IN_PROGRESS, ASSIGNED, Role.ADMIN),
            new Edge(ON_HOLD, ASSIGNED, Role.ADMIN),
            new Edge(REOPENED, ASSIGNED, Role.ADMIN),
            // Resident answers the allocation prompt
            new Edge(PENDING_RESIDENT_APPROVAL, ASSIGNED, Role.RESIDENT),       // approve
            new Edge(PENDING_RESIDENT_APPROVAL, ACKNOWLEDGED, Role.RESIDENT),   // decline
            // Provider
            new Edge(ASSIGNED, ACCEPTED, Role.PROVIDER),
            new Edge(ASSIGNED, REJECTED, Role.PROVIDER),
            new Edge(ACCEPTED, IN_PROGRESS, Role.PROVIDER),
            new Edge(IN_PROGRESS, ON_HOLD, Role.PROVIDER),
            new Edge(IN_PROGRESS, RESOLVED, Role.PROVIDER),
            new Edge(ON_HOLD, IN_PROGRESS, Role.PROVIDER),
            new Edge(ON_HOLD, RESOLVED, Role.PROVIDER),
            new Edge(REOPENED, IN_PROGRESS, Role.PROVIDER),
            new Edge(REOPENED, RESOLVED, Role.PROVIDER),
            // Resident
            new Edge(RESOLVED, REOPENED, Role.RESIDENT),
            new Edge(RESOLVED, CLOSED, Role.RESIDENT),
            // System
            new Edge(RESOLVED, CLOSED, null)
    );

    public boolean isLegal(TicketStatus from, TicketStatus to, Role actor) {
        return EDGES.contains(new Edge(from, to, actor));
    }

    public void assertTransition(TicketStatus from, TicketStatus to, Role actor) {
        if (!isLegal(from, to, actor)) {
            throw AppException.illegalTransition(
                    "Cannot move ticket from " + from + " to " + to
                    + (actor != null ? " as " + actor : " by system"));
        }
    }
}
