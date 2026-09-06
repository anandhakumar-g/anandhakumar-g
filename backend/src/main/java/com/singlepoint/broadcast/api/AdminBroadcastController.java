package com.singlepoint.broadcast.api;

import com.singlepoint.broadcast.BroadcastRepository;
import com.singlepoint.broadcast.BroadcastService;
import com.singlepoint.broadcast.domain.Broadcast;
import com.singlepoint.common.dto.PageResponse;
import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.security.AppPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/broadcasts")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@Tag(name = "Admin — Announcements", description = "Broadcast to every active resident of the acting community")
public class AdminBroadcastController {

    private final BroadcastService broadcastService;
    private final BroadcastRepository broadcasts;

    public AdminBroadcastController(BroadcastService broadcastService, BroadcastRepository broadcasts) {
        this.broadcastService = broadcastService;
        this.broadcasts = broadcasts;
    }

    private UUID tenant(AppPrincipal p) {
        if (p.getTenantId() == null) throw new AppException(ErrorCode.FORBIDDEN, "No active community");
        return p.getTenantId();
    }

    @PostMapping
    @Operation(summary = "Announce to every active resident of the acting community (optionally scheduled)")
    public ResponseEntity<BroadcastDtos.BroadcastView> send(@AuthenticationPrincipal AppPrincipal principal,
                                                            @Valid @RequestBody BroadcastDtos.SendRequest body) {
        Broadcast b = broadcastService.send(principal, Broadcast.Scope.COMMUNITY, tenant(principal),
                body.title().trim(), body.body().trim(), body.scheduledFor());
        return ResponseEntity.status(HttpStatus.CREATED).body(broadcastService.toView(b));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel a scheduled announcement that has not gone out yet")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal AppPrincipal principal, @PathVariable UUID id) {
        broadcastService.cancel(principal, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "Past announcements for the acting community")
    public ResponseEntity<PageResponse<BroadcastDtos.BroadcastView>> list(
            @AuthenticationPrincipal AppPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = broadcasts.findByTenantIdOrderByCreatedAtDesc(tenant(principal),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        return ResponseEntity.ok(PageResponse.of(result, broadcastService::toView));
    }
}
