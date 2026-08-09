package com.sep.comiverse.controller;

import com.sep.comiverse.dto.request.CreateReportCategoryRequest;
import com.sep.comiverse.dto.request.UpdateReportCategoryRequest;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.dto.response.ReportCategoryResponse;
import com.sep.comiverse.entity.enums.ReportAssignedRole;
import com.sep.comiverse.entity.enums.ReportTargetType;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.ReportCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({ "/report-categories"})
@RequiredArgsConstructor
@Tag(name = "Report Categories", description = "Endpoints for managing report categories and roles")
public class ReportCategoryController {

    private final ReportCategoryService reportCategoryService;

    @GetMapping
    @Operation(summary = "List active report categories", description = "Retrieve list of currently active report categories filtered by target type or role")
    public ResponseEntity<BaseResponse<List<ReportCategoryResponse>>> getActiveCategories(
            @RequestParam(name = "target_type", required = false) ReportTargetType targetType,
            @RequestParam(name = "assigned_role", required = false) ReportAssignedRole assignedRole
    ) {
        List<ReportCategoryResponse> response = reportCategoryService.getActiveCategories(targetType, assignedRole);
        return ResponseEntity.ok(
                BaseResponse.<List<ReportCategoryResponse>>builder()
                        .success(true)
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasAnyAuthority('MODERATOR', 'PROJECT_LEADER', 'ADMIN')")
    @Operation(summary = "Manage report categories (Admin/Mod/Leader)", description = "Retrieve all report categories including active and inactive records with target type and role filtering")
    public ResponseEntity<BaseResponse<List<ReportCategoryResponse>>> getAllCategories(
            @RequestParam(name = "is_active", required = false) Boolean isActive,
            @RequestParam(name = "assigned_role", required = false) ReportAssignedRole assignedRole,
            @RequestParam(name = "target_type", required = false) ReportTargetType targetType
    ) {
        List<ReportCategoryResponse> response = reportCategoryService.getAllCategories(isActive, assignedRole, targetType);
        return ResponseEntity.ok(
                BaseResponse.<List<ReportCategoryResponse>>builder()
                        .success(true)
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get report category by ID", description = "Retrieve details of a specific report category")
    public ResponseEntity<BaseResponse<ReportCategoryResponse>> getCategoryById(@PathVariable UUID id) {
        ReportCategoryResponse response = reportCategoryService.getCategoryById(id);
        return ResponseEntity.ok(
                BaseResponse.<ReportCategoryResponse>builder()
                        .success(true)
                        .data(response)
                        .build()
        );
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('MODERATOR', 'ADMIN')")
    @Operation(summary = "Create new report category (Mod/Admin)", description = "Add a new report category and assign the handling role")
    public ResponseEntity<BaseResponse<ReportCategoryResponse>> createCategory(
            @Valid @RequestBody CreateReportCategoryRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ReportCategoryResponse response = reportCategoryService.createCategory(
                principal != null ? principal.user() : null,
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.<ReportCategoryResponse>builder()
                        .success(true)
                        .data(response)
                        .message("Report category created successfully")
                        .build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('MODERATOR', 'ADMIN')")
    @Operation(summary = "Update report category (Mod/Admin)", description = "Update report category name, description, assigned handling role, or active status")
    public ResponseEntity<BaseResponse<ReportCategoryResponse>> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateReportCategoryRequest request
    ) {
        ReportCategoryResponse response = reportCategoryService.updateCategory(id, request);
        return ResponseEntity.ok(
                BaseResponse.<ReportCategoryResponse>builder()
                        .success(true)
                        .data(response)
                        .message("Report category updated successfully")
                        .build()
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('MODERATOR', 'ADMIN')")
    @Operation(summary = "Soft delete report category (Mod/Admin)", description = "Deactivate or soft delete a report category")
    public ResponseEntity<BaseResponse<Void>> deleteCategory(@PathVariable UUID id) {
        reportCategoryService.deleteCategory(id);
        return ResponseEntity.ok(
                BaseResponse.<Void>builder()
                        .success(true)
                        .message("Report category deleted successfully")
                        .build()
        );
    }
}
