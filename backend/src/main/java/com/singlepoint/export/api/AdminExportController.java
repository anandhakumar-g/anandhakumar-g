package com.singlepoint.export.api;

import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.export.ExportService;
import com.singlepoint.security.AppPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/exports")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@Tag(name = "Admin — Exports", description = "CSV downloads for the acting community")
public class AdminExportController {

    private final ExportService exportService;

    public AdminExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    private UUID tenant(AppPrincipal p) {
        if (p.getTenantId() == null) throw new AppException(ErrorCode.FORBIDDEN, "No active community");
        return p.getTenantId();
    }

    @GetMapping("/tickets.csv")
    @Operation(summary = "Tickets in this community (from / to / status)")
    public ResponseEntity<byte[]> tickets(@AuthenticationPrincipal AppPrincipal p,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String status) {
        UUID t = tenant(p);
        return Csv.download("tickets", w -> exportService.tickets(w, t, from, to, status));
    }

    @GetMapping("/payments.csv")
    @Operation(summary = "Service payments in this community (from / to / status)")
    public ResponseEntity<byte[]> payments(@AuthenticationPrincipal AppPrincipal p,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String status) {
        UUID t = tenant(p);
        return Csv.download("payments", w -> exportService.payments(w, t, from, to, status));
    }

    @GetMapping("/members.csv")
    @Operation(summary = "Active members of this community")
    public ResponseEntity<byte[]> members(@AuthenticationPrincipal AppPrincipal p) {
        UUID t = tenant(p);
        return Csv.download("members", w -> exportService.members(w, t));
    }
}
