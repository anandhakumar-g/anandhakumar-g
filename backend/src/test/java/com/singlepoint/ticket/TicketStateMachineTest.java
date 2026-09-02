package com.singlepoint.ticket;

import com.singlepoint.common.error.AppException;
import com.singlepoint.ticket.domain.TicketStatus;
import com.singlepoint.user.domain.Role;
import org.junit.jupiter.api.Test;

import static com.singlepoint.ticket.domain.TicketStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class TicketStateMachineTest {

    private final TicketStateMachine sm = new TicketStateMachine();

    @Test
    void adminHappyPath() {
        assertTrue(sm.isLegal(NEW, ASSIGNED, Role.ADMIN));
        assertTrue(sm.isLegal(NEW, ACKNOWLEDGED, Role.ADMIN));
        assertTrue(sm.isLegal(NEW, RESOLVED, Role.ADMIN));
        assertTrue(sm.isLegal(ACKNOWLEDGED, ASSIGNED, Role.ADMIN));
    }

    @Test
    void providerHappyPath() {
        assertTrue(sm.isLegal(ASSIGNED, ACCEPTED, Role.PROVIDER));
        assertTrue(sm.isLegal(ACCEPTED, IN_PROGRESS, Role.PROVIDER));
        assertTrue(sm.isLegal(IN_PROGRESS, ON_HOLD, Role.PROVIDER));
        assertTrue(sm.isLegal(ON_HOLD, IN_PROGRESS, Role.PROVIDER));
        assertTrue(sm.isLegal(IN_PROGRESS, RESOLVED, Role.PROVIDER));
    }

    @Test
    void residentClosesOrReopens() {
        assertTrue(sm.isLegal(RESOLVED, CLOSED, Role.RESIDENT));
        assertTrue(sm.isLegal(RESOLVED, REOPENED, Role.RESIDENT));
        assertTrue(sm.isLegal(RESOLVED, CLOSED, null)); // system auto-close
    }

    @Test
    void adminMayRerouteFromAnyLiveAssignmentState() {
        for (TicketStatus from : new TicketStatus[]{ASSIGNED, ACCEPTED, IN_PROGRESS, ON_HOLD, REJECTED, REOPENED}) {
            assertTrue(sm.isLegal(from, ASSIGNED, Role.ADMIN), "reroute from " + from);
        }
    }

    @Test
    void allocationApprovalGate() {
        // Admin parks a ticket for the resident to approve the proposed helper.
        assertTrue(sm.isLegal(NEW, PENDING_RESIDENT_APPROVAL, Role.ADMIN));
        assertTrue(sm.isLegal(ACKNOWLEDGED, PENDING_RESIDENT_APPROVAL, Role.ADMIN));
        assertTrue(sm.isLegal(ASSIGNED, PENDING_RESIDENT_APPROVAL, Role.ADMIN)); // re-pick before answer
        // Resident answers.
        assertTrue(sm.isLegal(PENDING_RESIDENT_APPROVAL, ASSIGNED, Role.RESIDENT));       // approve
        assertTrue(sm.isLegal(PENDING_RESIDENT_APPROVAL, ACKNOWLEDGED, Role.RESIDENT));   // decline
        // Admin override once the gate is off.
        assertTrue(sm.isLegal(PENDING_RESIDENT_APPROVAL, ASSIGNED, Role.ADMIN));
        // The provider stays locked out while parked.
        assertFalse(sm.isLegal(PENDING_RESIDENT_APPROVAL, ACCEPTED, Role.PROVIDER));
        assertFalse(sm.isLegal(PENDING_RESIDENT_APPROVAL, ASSIGNED, Role.PROVIDER));
    }

    @Test
    void illegalTransitionsRejected() {
        assertFalse(sm.isLegal(NEW, IN_PROGRESS, Role.PROVIDER));
        assertFalse(sm.isLegal(RESOLVED, NEW, Role.PROVIDER));
        assertFalse(sm.isLegal(CLOSED, REOPENED, Role.RESIDENT));
        assertFalse(sm.isLegal(ASSIGNED, ACCEPTED, Role.RESIDENT)); // wrong actor
        assertThrows(AppException.class, () -> sm.assertTransition(NEW, IN_PROGRESS, Role.PROVIDER));
    }
}
