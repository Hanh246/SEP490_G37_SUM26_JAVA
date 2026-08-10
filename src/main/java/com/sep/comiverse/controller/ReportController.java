package com.sep.comiverse.controller;

import com.sep.comiverse.dto.request.CreateReportRequest;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.dto.response.ReportCategoryResponse;
import com.sep.comiverse.dto.response.ReportResponse;
import com.sep.comiverse.entity.enums.ReportAssignedRole;
import com.sep.comiverse.entity.enums.ReportTargetType;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.ReportCategoryService;
import com.sep.comiverse.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/reports"})
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Endpoints for readers and authors to submit and track issue reports")
public class ReportController {

    private final ReportService reportService;
    private final ReportCategoryService reportCategoryService;

    /**
     * API 1: Create a new report
     * POST /api/v1/reports (or /v1/reports)
     * Auth Required: Authenticated user (USER, READER, AUTHOR, TRANSLATOR, PROJECT_LEADER, MODERATOR, ADMIN)
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Submit a new issue report", description = "Submit an issue report for a Comic, Chapter, or Chapter Translation with status-based duplicate lock and validation")
    public ResponseEntity<BaseResponse<ReportResponse>> createReport(
            @Valid @RequestBody CreateReportRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID reporterId = principal.getId();
        ReportResponse response = reportService.createReport(reporterId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.<ReportResponse>builder()
                        .success(true)
                        .data(response)
                        .message("Report created successfully")
                        .build());
    }

    /**
     * Get active report categories for selection
     * GET /api/v1/reports/categories?target_type=COMIC
     */
    @GetMapping("/categories")
    @Operation(summary = "Get active report categories", description = "Retrieve list of active report categories optionally filtered by target type (COMIC, CHAPTER, CHAPTER_TRANSLATIONS)")
    public ResponseEntity<BaseResponse<List<ReportCategoryResponse>>> getActiveCategories(
            @RequestParam(name = "target_type", required = false) ReportTargetType targetType,
            @RequestParam(name = "assigned_role", required = false) ReportAssignedRole assignedRole
    ) {
        List<ReportCategoryResponse> categories = reportCategoryService.getActiveCategories(targetType, assignedRole);
        return ResponseEntity.ok(
                BaseResponse.<List<ReportCategoryResponse>>builder()
                        .success(true)
                        .data(categories)
                        .build()
        );
    }

    /**
     * Get report history of the currently authenticated user
     * GET /api/v1/reports/my-reports
     */
    @GetMapping("/my-reports")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get current user's submitted reports", description = "Retrieve paginated list of reports submitted by the logged-in user")
    public ResponseEntity<BaseResponse<Page<ReportResponse>>> getMyReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ReportResponse> response = reportService.getMyReports(principal.getId(), pageable);
        return ResponseEntity.ok(
                BaseResponse.<Page<ReportResponse>>builder()
                        .success(true)
                        .data(response)
                        .build()
        );
    }

    /**
     * Get detail of a specific report by ID
     * GET /api/v1/reports/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get report details", description = "View details of a report by ID (reporter or moderator/leader/admin)")
    public ResponseEntity<BaseResponse<ReportResponse>> getReportById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ReportResponse response = reportService.getReportById(id, principal != null ? principal.user() : null);
        return ResponseEntity.ok(
                BaseResponse.<ReportResponse>builder()
                        .success(true)
                        .data(response)
                        .build()
        );
    }
}
