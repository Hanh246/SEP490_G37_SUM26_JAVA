package com.sep.comiverse.controller;

import com.sep.comiverse.dto.response.AdminStatisticsResponse;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.service.AdminStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/statistics")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ADMIN', 'MODERATOR')")
@Tag(name = "Admin - Statistics", description = "Accurate platform statistics for the Admin dashboard")
public class AdminStatisticsController {

    private final AdminStatisticsService adminStatisticsService;

    @GetMapping
    @Operation(summary = "Get platform statistics")
    public ResponseEntity<BaseResponse<AdminStatisticsResponse>> getStatistics() {
        return ResponseEntity.ok(
                BaseResponse.<AdminStatisticsResponse>builder()
                        .success(true)
                        .data(adminStatisticsService.getStatistics())
                        .build()
        );
    }
}
