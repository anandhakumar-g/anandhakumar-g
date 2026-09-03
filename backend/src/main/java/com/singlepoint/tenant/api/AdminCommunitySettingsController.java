package com.singlepoint.tenant.api;

import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.security.AppPrincipal;
import com.singlepoint.tenant.TenantService;
import com.singlepoint.tenant.domain.Tenant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.UUID;

/**
 * Community-admin view of its own settings. The admin may set the reopen window and the
 * resident approval-of-allocation gate; {@code categoryAdmin} is Super-Admin territory
 * (see {@link SuperAdminController#updateTenant}).
 */
@RestController
@RequestMapping("/api/v1/admin/community-settings")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@Tag(name = "Admin — Community settings", description = "Reopen window and the approval-of-allocation gate")
public class AdminCommunitySettingsController {

    private final TenantService tenantService;

    public AdminCommunitySettingsController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    private UUID tenant(AppPrincipal p) {
        if (p.getTenantId() == null) throw new AppException(ErrorCode.FORBIDDEN, "No active community");
        return p.getTenantId();
    }

    @GetMapping
    @Operation(summary = "This community's settings")
    public ResponseEntity<TenantDtos.TenantSettingsView> get(@AuthenticationPrincipal AppPrincipal principal) {
        return ResponseEntity.ok(TenantDtos.TenantSettingsView.from(tenantService.require(tenant(principal))));
    }

    @PutMapping
    @Operation(summary = "Update the reopen window / approval-of-allocation gate")
    public ResponseEntity<TenantDtos.TenantSettingsView> update(@AuthenticationPrincipal AppPrincipal principal,
            @Valid @RequestBody TenantDtos.CommunitySettingsRequest body) {
        Tenant t = tenantService.update(tenant(principal), null, null, null, null, null, null, null, null,
                body.reopenWindowHours(), body.requireAllocationApproval(), null, body.directServiceEnabled());
        return ResponseEntity.ok(TenantDtos.TenantSettingsView.from(t));
    }
}
