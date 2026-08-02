package com.sep.comiverse.controller;

import com.sep.comiverse.dto.response.AuthorDashboardMetricsResponse;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.AuthorDashboardMetricService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/author/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('AUTHOR')")
@Tag(name = "Author - Dashboard", description = "Aggregated metrics for the author dashboard")
public class AuthorDashboardController {

    private final AuthorDashboardMetricService authorDashboardMetricService;

    @GetMapping("/metrics")
    @Operation(summary = "Get author dashboard metrics", description = "Returns summary cards, monthly chart data, top comics, and recent review activity for the authenticated author")
    public ResponseEntity<BaseResponse<AuthorDashboardMetricsResponse>> getDashboardMetrics(
            @RequestParam(value = "period", required = false, defaultValue = "WEEK") String period,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new CustomException(401, "Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        return ResponseEntity.ok(BaseResponse.<AuthorDashboardMetricsResponse>builder()
                .success(true)
                .data(authorDashboardMetricService.getDashboardMetrics(principal.getId(), period))
                .build());
    }
}
