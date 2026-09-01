package com.singlepoint.ticket.domain;

public enum TicketStatus {
    NEW, ACKNOWLEDGED, ASSIGNED, ACCEPTED, REJECTED,
    IN_PROGRESS, ON_HOLD, RESOLVED, CLOSED, REOPENED;

    public boolean isTerminal() {
        return this == CLOSED;
    }

    public boolean isOpen() {
        return this != CLOSED;
    }
}
