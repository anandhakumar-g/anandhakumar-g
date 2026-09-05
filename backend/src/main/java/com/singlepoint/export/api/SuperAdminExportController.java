package com.singlepoint.export.api;

import com.singlepoint.export.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/superadmin/exports")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Super Admin — Exports", description = "Platform-wide CSV downloads")
public class SuperAdminExportController {

    private final ExportService exportService;

    public SuperAdminExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/tickets.csv")
    @Operation(summary = "Tickets across the platform (from / to / status / tenantId)")
    public ResponseEntity<byte[]> tickets(
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String status, @RequestParam(required = false) UUID tenantId) {
        return Csv.download("tickets", w -> exportService.tickets(w, tenantId, from, to, status));
    }

    @GetMapping("/offers.csv")
    @Operation(summary = "Offers across the platform (from / to / status)")
    public ResponseEntity<byte[]> offers(
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String status) {
        return Csv.download("offers", w -> exportService.offers(w, from, to, status));
    }

    @GetMapping("/communities.csv")
    @Operation(summary = "Every community + its ticket counts")
    public ResponseEntity<byte[]> communities() {
        return Csv.download("communities", exportService::communities);
    }
}
