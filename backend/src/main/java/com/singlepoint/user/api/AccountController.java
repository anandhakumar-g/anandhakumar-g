package com.singlepoint.user.api;

import com.singlepoint.security.AppPrincipal;
import com.singlepoint.user.AccountExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.singlepoint.common.error.AppException;
import com.singlepoint.notification.DeviceTokenRepository;
import com.singlepoint.notification.domain.DeviceToken;
import com.singlepoint.user.AccountDeletionService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** MVP-13 (C): account self-service — data export (C1), deletion (C2), device management (C3). */
@RestController
@RequestMapping("/api/v1/me")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Me — Account", description = "Data export, account deletion, device sessions")
public class AccountController {

    private final AccountExportService exportService;
    private final AccountDeletionService deletionService;
    private final DeviceTokenRepository devices;

    public AccountController(AccountExportService exportService, AccountDeletionService deletionService,
                            DeviceTokenRepository devices) {
        this.exportService = exportService;
        this.deletionService = deletionService;
        this.devices = devices;
    }

    public record DeviceView(UUID id, String label, String platform, Instant lastSeenAt, boolean current,
                             boolean revoked) { }

    @GetMapping("/export")
    @Operation(summary = "Download everything the platform holds about you, as JSON")
    public ResponseEntity<Map<String, Object>> export(@AuthenticationPrincipal AppPrincipal p) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"single-point-export-" + LocalDate.now() + ".json\"")
                .body(exportService.export(p.getUserId()));
    }

    @DeleteMapping
    @Operation(summary = "Close your account — soft-delete + anonymize; blocked while you have open "
            + "requests, unsettled bills, or are the sole admin of a community")
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal AppPrincipal p) {
        deletionService.deleteMe(p.getUserId());
        return ResponseEntity.noContent().build();
    }

    // ---- MVP-13 (C3): device / session management ------------------------

    @GetMapping("/devices")
    @Operation(summary = "The devices signed in to your account")
    public ResponseEntity<List<DeviceView>> devices(@AuthenticationPrincipal AppPrincipal p) {
        var list = devices.findByUserIdAndDeviceIdIsNotNullOrderByLastSeenAtDesc(p.getUserId()).stream()
                .map(d -> new DeviceView(d.getId(), d.getLabel(),
                        d.getPlatform() != null ? d.getPlatform().name() : null,
                        d.getLastSeenAt(), d.getDeviceId().equals(p.getDeviceId()), d.getRevokedAt() != null))
                .toList();
        return ResponseEntity.ok(list);
    }

    @PostMapping("/devices/{id}/revoke")
    @Operation(summary = "Sign one device out — its next request is rejected")
    @Transactional
    public ResponseEntity<Void> revokeDevice(@AuthenticationPrincipal AppPrincipal p, @PathVariable UUID id) {
        DeviceToken d = devices.findById(id)
                .filter(x -> x.getUserId().equals(p.getUserId()) && x.getDeviceId() != null)
                .orElseThrow(() -> AppException.notFound("Device"));
        if (d.getRevokedAt() == null) {
            d.setRevokedAt(Instant.now());
            devices.save(d);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/devices/revoke-others")
    @Operation(summary = "Sign every device out except this one")
    @Transactional
    public ResponseEntity<Void> revokeOtherDevices(@AuthenticationPrincipal AppPrincipal p) {
        for (DeviceToken d : devices.findByUserIdAndDeviceIdIsNotNullOrderByLastSeenAtDesc(p.getUserId())) {
            if (d.getRevokedAt() == null && !d.getDeviceId().equals(p.getDeviceId())) {
                d.setRevokedAt(Instant.now());
                devices.save(d);
            }
        }
        return ResponseEntity.noContent().build();
    }
}

