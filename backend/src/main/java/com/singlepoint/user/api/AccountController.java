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
import com.singlepoint.user.AccountDeletionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/** MVP-13 (C): account self-service — data export (C1), deletion (C2), device management (C3). */
@RestController
@RequestMapping("/api/v1/me")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Me — Account", description = "Data export, account deletion, device sessions")
public class AccountController {

    private final AccountExportService exportService;
    private final AccountDeletionService deletionService;

    public AccountController(AccountExportService exportService, AccountDeletionService deletionService) {
        this.exportService = exportService;
        this.deletionService = deletionService;
    }

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
}

