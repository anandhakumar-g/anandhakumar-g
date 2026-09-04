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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/superadmin/broadcasts")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Super Admin — Announcements", description = "Broadcast to all admins, all users, or a named community")
public class SuperAdminBroadcastController {

    private final BroadcastService broadcastService;
    private final BroadcastRepository broadcasts;

    public SuperAdminBroadcastController(BroadcastService broadcastService, BroadcastRepository broadcasts) {
        this.broadcastService = broadcastService;
        this.broadcasts = broadcasts;
    }

    @PostMapping
    @Operation(summary = "Send a platform-wide or community announcement")
    public ResponseEntity<BroadcastDtos.BroadcastView> send(@AuthenticationPrincipal AppPrincipal principal,
                                                            @Valid @RequestBody BroadcastDtos.SendRequest body) {
        Broadcast.Scope scope = parseScope(body.scope());
        UUID tenantId = scope == Broadcast.Scope.COMMUNITY ? parseTenantId(body.tenantId()) : null;
        Broadcast b = broadcastService.send(principal, scope, tenantId, body.title().trim(), body.body().trim());
        return ResponseEntity.status(HttpStatus.CREATED).body(broadcastService.toView(b));
    }

    @GetMapping
    @Operation(summary = "Past announcements (optionally one scope)")
    public ResponseEntity<PageResponse<BroadcastDtos.BroadcastView>> list(
            @RequestParam(required = false) String scope,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Broadcast.Scope> scopes = scope == null || scope.isBlank()
                ? List.of(Broadcast.Scope.values())
                : List.of(parseScope(scope));
        var result = broadcasts.findByScopeInOrderByCreatedAtDesc(scopes,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        return ResponseEntity.ok(PageResponse.of(result, broadcastService::toView));
    }

    private static Broadcast.Scope parseScope(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "scope is required (COMMUNITY, ALL_ADMINS or ALL_USERS)");
        }
        try {
            return Broadcast.Scope.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Unknown scope: " + raw);
        }
    }

    private static UUID parseTenantId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "tenantId is required for a COMMUNITY announcement");
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "tenantId is not a valid id");
        }
    }
}
