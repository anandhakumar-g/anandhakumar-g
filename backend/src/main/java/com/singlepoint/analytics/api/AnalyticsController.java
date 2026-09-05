package com.singlepoint.analytics.api;

import com.singlepoint.analytics.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/superadmin/analytics")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Super Admin — Analytics", description = "Platform metrics over time + now totals")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping
    @Operation(summary = "Time-bucketed series (tickets, offers, users, revenue) + platform totals")
    public ResponseEntity<AnalyticsDtos.AnalyticsView> platform(
            @RequestParam(defaultValue = "WEEK") String bucket,
            @RequestParam(defaultValue = "12") int points) {
        return ResponseEntity.ok(analyticsService.platform(bucket, points));
    }
}
