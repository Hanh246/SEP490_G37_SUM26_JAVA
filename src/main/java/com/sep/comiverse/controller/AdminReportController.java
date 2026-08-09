package com.sep.comiverse.controller;

import com.sep.comiverse.dto.request.ProcessReportRequest;
import com.sep.comiverse.dto.request.ReportFilterDTO;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.dto.response.ReportResponse;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping({"/admin/reports"})
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('MODERATOR', 'PROJECT_LEADER', 'ADMIN')")
@Tag(name = "Admin Reports", description = "Endpoints for Moderators and Project Leaders to manage and process issue reports")
public class AdminReportController {

    private final ReportService reportService;

    /**
     * API 2: Moderator/Leader get paginated list of reports (Filtering & Pagination)
     * GET /api/v1/admin/reports (or /v1/admin/reports)
     * Auth Required: MODERATOR, PROJECT_LEADER, ADMIN
     * Query Params: page, size, status, target_type, category_id, assigned_role
     * Flow:
     * - Filter and paginate reports.
     * - Automatically filter according to category assigned_role matching current user's role
     *   (Moderators see MODERATOR assigned categories, Project Leaders see PROJECT_LEADER assigned categories, Admins see all).
     */
    @GetMapping
    @Operation(
            summary = "List reports with filtering & pagination (Mod/Leader/Admin)",
            description = "Retrieve paginated list of issue reports with role-based routing and filters for status, target type, category, and date."
    )
    public ResponseEntity<BaseResponse<Page<ReportResponse>>> getReports(
            @Valid @ParameterObject ReportFilterDTO filterDTO,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Page<ReportResponse> response = reportService.getReports(principal != null ? principal.user() : null, filterDTO);
        return ResponseEntity.ok(
                BaseResponse.<Page<ReportResponse>>builder()
                        .success(true)
                        .data(response)
                        .build()
        );
    }

    /**
     * API 3: Moderator/Leader update report status (Accept / Reject)
     * PATCH /api/v1/admin/reports/{id}/process (or /v1/admin/reports/{id}/process)
     * Auth Required: MODERATOR, PROJECT_LEADER, ADMIN
     * Request Body: { action: "ACCEPT" | "REJECT", resolution_note: string }
     */
    @PatchMapping("/{id}/process")
    @Operation(
            summary = "Process report status (Accept / Reject)",
            description = "Accept or reject an issue report, update resolver status and timestamp, and automatically dispatch a notification to the reporting user."
    )
    public ResponseEntity<BaseResponse<ReportResponse>> processReportPatch(
            @PathVariable UUID id,
            @Valid @RequestBody ProcessReportRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ReportResponse response = reportService.processReport(id, principal != null ? principal.user() : null, request);
        return ResponseEntity.ok(
                BaseResponse.<ReportResponse>builder()
                        .success(true)
                        .data(response)
                        .message("Report processed successfully")
                        .build()
        );
    }

    /**
     * PUT support for report processing
     */
    @PutMapping("/{id}/process")
    @Operation(
            summary = "Process report status (PUT alternative)",
            description = "Accept or reject an issue report (supports both PUT and PATCH)."
    )
    public ResponseEntity<BaseResponse<ReportResponse>> processReportPut(
            @PathVariable UUID id,
            @Valid @RequestBody ProcessReportRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ReportResponse response = reportService.processReport(id, principal != null ? principal.user() : null, request);
        return ResponseEntity.ok(
                BaseResponse.<ReportResponse>builder()
                        .success(true)
                        .data(response)
                        .message("Report processed successfully")
                        .build()
        );
    }

    /**
     * Get report detail for Moderator / Leader / Admin
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get report details for Mod/Leader/Admin", description = "View detailed report information including target and reporter information.")
    public ResponseEntity<BaseResponse<ReportResponse>> getReportDetail(
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
