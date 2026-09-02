package com.singlepoint.ticket.api;

import com.singlepoint.common.dto.PageResponse;
import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.security.AppPrincipal;
import com.singlepoint.ticket.TicketService;
import com.singlepoint.ticket.domain.Ticket;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets")
@Tag(name = "Tickets", description = "Raise, triage, resolve — role-aware")
public class TicketController {

    private final TicketService ticketService;
    private final TicketMapper mapper;

    public TicketController(TicketService ticketService, TicketMapper mapper) {
        this.ticketService = ticketService;
        this.mapper = mapper;
    }

    // ---- create / read ----

    @PostMapping
    @PreAuthorize("hasRole('RESIDENT')")
    @Operation(summary = "Raise a ticket")
    public ResponseEntity<TicketDtos.TicketView> raise(@AuthenticationPrincipal AppPrincipal principal,
                                                       @Valid @RequestBody TicketDtos.RaiseRequest body) {
        Ticket t = ticketService.raise(principal, new TicketService.RaiseCommand(
                UUID.fromString(body.categoryId()),
                body.subcategoryId() != null ? UUID.fromString(body.subcategoryId()) : null,
                body.description(), body.priority(), body.serviceAddressText(),
                body.serviceGeoLat(), body.serviceGeoLng(), body.serviceLandmark(),
                body.preferredTimeWindow(),
                body.flatId() != null ? UUID.fromString(body.flatId()) : null));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toView(t, principal));
    }

    @GetMapping
    @Operation(summary = "List tickets in scope for the caller's role")
    public ResponseEntity<PageResponse<TicketDtos.TicketView>> list(
            @AuthenticationPrincipal AppPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = ticketService.list(principal, status, PageRequest.of(page, Math.min(size, 100)));
        return ResponseEntity.ok(PageResponse.of(result, t -> mapper.toView(t, principal)));
    }

    @GetMapping("/{id}")
    @com.singlepoint.audit.AuditRead(entity = "ticket")
    public ResponseEntity<TicketDtos.TicketView> get(@AuthenticationPrincipal AppPrincipal principal,
                                                     @PathVariable UUID id) {
        return ResponseEntity.ok(mapper.toView(ticketService.getForActor(principal, id), principal));
    }

    @GetMapping("/{id}/timeline")
    public ResponseEntity<List<TicketDtos.TimelineEntry>> timeline(@AuthenticationPrincipal AppPrincipal principal,
                                                                   @PathVariable UUID id) {
        return ResponseEntity.ok(ticketService.timeline(principal, id).stream().map(mapper::toTimeline).toList());
    }

    @GetMapping("/{id}/attachments")
    public ResponseEntity<List<TicketDtos.AttachmentView>> attachments(@AuthenticationPrincipal AppPrincipal principal,
                                                                       @PathVariable UUID id) {
        return ResponseEntity.ok(ticketService.attachments(principal, id).stream().map(mapper::toAttachment).toList());
    }

    @PostMapping(value = "/{id}/attachments", consumes = "multipart/form-data")
    @Operation(summary = "Attach a photo / video to a ticket")
    public ResponseEntity<TicketDtos.AttachmentView> upload(@AuthenticationPrincipal AppPrincipal principal,
                                                            @PathVariable UUID id,
                                                            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) throw new AppException(ErrorCode.VALIDATION_FAILED, "file is empty");
        try {
            var a = ticketService.addAttachment(principal, id, file.getOriginalFilename(),
                    file.getContentType(), file.getBytes());
            return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toAttachment(a));
        } catch (IOException e) {
            throw new AppException(ErrorCode.STORAGE_ERROR, "Could not read upload", e);
        }
    }

    // ---- admin transitions ----

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TicketDtos.TicketView> acknowledge(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID id, @RequestBody(required = false) TicketDtos.RemarksRequest body) {
        return ResponseEntity.ok(mapper.toView(
                ticketService.acknowledge(p, id, body != null ? body.remarks() : null), p));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TicketDtos.TicketView> resolveDirect(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID id, @Valid @RequestBody TicketDtos.ResolveRequest body) {
        return ResponseEntity.ok(mapper.toView(ticketService.resolveDirect(p, id, body.resolutionNotes()), p));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TicketDtos.TicketView> assign(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID id, @Valid @RequestBody TicketDtos.AssignRequest body) {
        return ResponseEntity.ok(mapper.toView(
                ticketService.assign(p, id, UUID.fromString(body.providerId()), body.remarks()), p));
    }

    @PostMapping("/{id}/reroute")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reassign to a different provider (same as assign; kept for clarity)")
    public ResponseEntity<TicketDtos.TicketView> reroute(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID id, @Valid @RequestBody TicketDtos.AssignRequest body) {
        return ResponseEntity.ok(mapper.toView(
                ticketService.assign(p, id, UUID.fromString(body.providerId()), body.remarks()), p));
    }

    // ---- provider transitions ----

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<TicketDtos.TicketView> accept(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID id) {
        return ResponseEntity.ok(mapper.toView(ticketService.providerRespond(p, id, true, null), p));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<TicketDtos.TicketView> reject(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID id, @RequestBody(required = false) TicketDtos.RejectRequest body) {
        return ResponseEntity.ok(mapper.toView(
                ticketService.providerRespond(p, id, false, body != null ? body.reason() : null), p));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasRole('PROVIDER')")
    @Operation(summary = "Provider moves the ticket to IN_PROGRESS / ON_HOLD / RESOLVED")
    public ResponseEntity<TicketDtos.TicketView> providerStatus(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID id, @Valid @RequestBody TicketDtos.ProviderStatusRequest body) {
        return ResponseEntity.ok(mapper.toView(
                ticketService.providerStatus(p, id, body.toStatus(), body.reason(), body.resolutionNotes()), p));
    }

    // ---- resident transitions ----

    @PostMapping("/{id}/allocation/approve")
    @PreAuthorize("hasRole('RESIDENT')")
    @Operation(summary = "Resident approves the proposed helper (communities with the approval gate on)")
    public ResponseEntity<TicketDtos.TicketView> approveAllocation(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID id) {
        return ResponseEntity.ok(mapper.toView(ticketService.approveAllocation(p, id), p));
    }

    @PostMapping("/{id}/allocation/reject")
    @PreAuthorize("hasRole('RESIDENT')")
    @Operation(summary = "Resident declines the proposed helper (a reason is required)")
    public ResponseEntity<TicketDtos.TicketView> rejectAllocation(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID id, @RequestBody(required = false) TicketDtos.RejectRequest body) {
        return ResponseEntity.ok(mapper.toView(
                ticketService.rejectAllocation(p, id, body != null ? body.reason() : null), p));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasRole('RESIDENT')")
    public ResponseEntity<TicketDtos.TicketView> reopen(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID id, @RequestBody(required = false) TicketDtos.RemarksRequest body) {
        return ResponseEntity.ok(mapper.toView(
                ticketService.reopen(p, id, body != null ? body.remarks() : null), p));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('RESIDENT')")
    public ResponseEntity<TicketDtos.TicketView> close(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID id, @RequestBody(required = false) TicketDtos.CloseRequest body) {
        Integer rating = body != null ? body.rating() : null;
        String remarks = body != null ? body.remarks() : null;
        return ResponseEntity.ok(mapper.toView(ticketService.close(p, id, rating, remarks), p));
    }
}
