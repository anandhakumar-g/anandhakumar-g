package com.singlepoint.tenant.api;

import com.singlepoint.common.dto.PageResponse;
import com.singlepoint.tenant.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants")
@Tag(name = "Communities", description = "Community directory for onboarding")
public class TenantLookupController {

    private final TenantService tenantService;

    public TenantLookupController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    @Operation(summary = "Search active communities by name / city / locality")
    public ResponseEntity<PageResponse<TenantDtos.TenantCard>> search(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = tenantService.search(query, PageRequest.of(page, Math.min(size, 50)));
        return ResponseEntity.ok(PageResponse.of(result, TenantDtos.TenantCard::from));
    }
}
