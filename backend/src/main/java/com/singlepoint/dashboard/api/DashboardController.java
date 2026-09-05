package com.singlepoint.dashboard.api;

import com.singlepoint.dashboard.DashboardService;
import com.singlepoint.security.AppPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "One at-a-glance summary, shaped by the caller's role")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @Operation(summary = "Tickets by status + the action items that need this caller")
    public ResponseEntity<DashboardDtos.DashboardView> get(@AuthenticationPrincipal AppPrincipal principal) {
        return ResponseEntity.ok(dashboardService.forPrincipal(principal));
    }
}
