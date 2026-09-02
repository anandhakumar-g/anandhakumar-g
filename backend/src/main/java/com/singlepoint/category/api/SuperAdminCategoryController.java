package com.singlepoint.category.api;

import com.singlepoint.category.TicketCategoryAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

/**
 * MVP-5 (A5): Super Admin manages ticket categories in either scope — the global catalogue
 * ({@code tenantId} absent) or a single community's own list ({@code ?tenantId=<uuid>}).
 */
@RestController
@RequestMapping("/api/v1/superadmin/ticket-categories")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Super Admin — Ticket categories", description = "Global catalogue + per-community lists")
public class SuperAdminCategoryController {

    private final TicketCategoryAdminService service;

    public SuperAdminCategoryController(TicketCategoryAdminService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List categories in a scope (global when tenantId is omitted), including inactive")
    public ResponseEntity<List<TicketCategoryDtos.CategoryView>> list(
            @RequestParam(required = false) UUID tenantId) {
        return ResponseEntity.ok(service.list(tenantId).stream()
                .map(TicketCategoryDtos.CategoryView::of).toList());
    }

    @PostMapping
    public ResponseEntity<TicketCategoryDtos.CategoryView> create(
            @RequestParam(required = false) UUID tenantId,
            @Valid @RequestBody TicketCategoryDtos.CreateRequest body) {
        var c = service.create(tenantId, body.name(), body.requestType(), body.parentCategoryId(),
                body.slaHours(), body.defaultProviderKind(), body.sortOrder());
        return ResponseEntity.status(HttpStatus.CREATED).body(TicketCategoryDtos.CategoryView.of(c));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TicketCategoryDtos.CategoryView> update(
            @RequestParam(required = false) UUID tenantId, @PathVariable UUID id,
            @RequestBody TicketCategoryDtos.UpdateRequest body) {
        var c = service.update(tenantId, id, body.name(), body.requestType(), body.parentCategoryId(),
                body.slaHours(), body.defaultProviderKind(), body.sortOrder());
        return ResponseEntity.ok(TicketCategoryDtos.CategoryView.of(c));
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Hide a category from the raise-ticket picker; existing tickets keep it")
    public ResponseEntity<TicketCategoryDtos.CategoryView> deactivate(
            @RequestParam(required = false) UUID tenantId, @PathVariable UUID id) {
        return ResponseEntity.ok(TicketCategoryDtos.CategoryView.of(service.setActive(tenantId, id, false)));
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<TicketCategoryDtos.CategoryView> reactivate(
            @RequestParam(required = false) UUID tenantId, @PathVariable UUID id) {
        return ResponseEntity.ok(TicketCategoryDtos.CategoryView.of(service.setActive(tenantId, id, true)));
    }
}
