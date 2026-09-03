package com.singlepoint.category.api;

import com.singlepoint.category.TicketCategoryAdminService;
import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.security.AppPrincipal;
import com.singlepoint.tenant.TenantService;
import com.singlepoint.tenant.domain.CategoryAdmin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

/**
 * MVP-5 (A5): a community admin manages its own ticket categories — only when the platform has
 * set {@code tenant.category_admin = COMMUNITY} for that community. The global catalogue is
 * never reachable here.
 */
@RestController
@RequestMapping("/api/v1/admin/ticket-categories")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@Tag(name = "Admin — Ticket categories", description = "A community's own ticket categories")
public class AdminCategoryController {

    private final TicketCategoryAdminService service;
    private final TenantService tenantService;

    public AdminCategoryController(TicketCategoryAdminService service, TenantService tenantService) {
        this.service = service;
        this.tenantService = tenantService;
    }

    private UUID requireCommunityCategoryAdmin(AppPrincipal p) {
        if (p.getTenantId() == null) throw new AppException(ErrorCode.FORBIDDEN, "No active community");
        if (tenantService.require(p.getTenantId()).getCategoryAdmin() != CategoryAdmin.COMMUNITY) {
            throw new AppException(ErrorCode.FORBIDDEN,
                    "The platform manages this community's ticket categories");
        }
        return p.getTenantId();
    }

    @GetMapping
    public ResponseEntity<List<TicketCategoryDtos.CategoryView>> list(@AuthenticationPrincipal AppPrincipal p) {
        return ResponseEntity.ok(service.list(requireCommunityCategoryAdmin(p)).stream()
                .map(TicketCategoryDtos.CategoryView::of).toList());
    }

    @PostMapping
    public ResponseEntity<TicketCategoryDtos.CategoryView> create(@AuthenticationPrincipal AppPrincipal p,
            @Valid @RequestBody TicketCategoryDtos.CreateRequest body) {
        var c = service.create(requireCommunityCategoryAdmin(p), body.name(), body.requestType(),
                body.parentCategoryId(), body.slaHours(), body.defaultProviderKind(), body.sortOrder());
        return ResponseEntity.status(HttpStatus.CREATED).body(TicketCategoryDtos.CategoryView.of(c));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TicketCategoryDtos.CategoryView> update(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID id, @RequestBody TicketCategoryDtos.UpdateRequest body) {
        var c = service.update(requireCommunityCategoryAdmin(p), id, body.name(), body.requestType(),
                body.parentCategoryId(), body.slaHours(), body.defaultProviderKind(), body.sortOrder());
        return ResponseEntity.ok(TicketCategoryDtos.CategoryView.of(c));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<TicketCategoryDtos.CategoryView> deactivate(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID id) {
        return ResponseEntity.ok(TicketCategoryDtos.CategoryView.of(
                service.setActive(requireCommunityCategoryAdmin(p), id, false)));
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<TicketCategoryDtos.CategoryView> reactivate(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID id) {
        return ResponseEntity.ok(TicketCategoryDtos.CategoryView.of(
                service.setActive(requireCommunityCategoryAdmin(p), id, true)));
    }
}
