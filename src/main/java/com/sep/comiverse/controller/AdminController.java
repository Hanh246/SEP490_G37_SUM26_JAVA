package com.sep.comiverse.controller;

import com.sep.comiverse.dto.pagination.AdminUserSearchDTO;
import com.sep.comiverse.dto.pagination.PaginationMetadata;
import com.sep.comiverse.dto.pagination.PaginationResponse;
import com.sep.comiverse.dto.response.AdminUserResponse;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
@Tag(name = "Admin - User Management", description = "APIs for managing user accounts (Admin only)")
public class AdminController {

    private final AdminUserService adminUserService;

    /**
     * GET /admin/users
     * List all users with search, role filter, status filter, and pagination.
     *
     * Query params: search, role, status, page, size
     */
    @GetMapping
    @Operation(summary = "List all users", description = "Retrieve paginated list of users with optional search, role, and status filters")
    public ResponseEntity<PaginationResponse<List<AdminUserResponse>>> getAllUsers(
            @Valid @ParameterObject AdminUserSearchDTO searchDTO
    ) {
        Page<AdminUserResponse> usersPage = adminUserService.getAllUsers(searchDTO);
        PaginationMetadata metadata = new PaginationMetadata(
                searchDTO.getPage(),
                searchDTO.getSize(),
                usersPage.getTotalElements(),
                usersPage.getTotalPages()
        );
        return ResponseEntity.ok(
                PaginationResponse.<List<AdminUserResponse>>builder()
                        .success(true)
                        .data(usersPage.getContent())
                        .metadata(metadata)
                        .build()
        );
    }

    /**
     * GET /admin/users/{id}
     * Get a single user's details.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get user details", description = "Retrieve a single user's details by ID")
    public ResponseEntity<BaseResponse<AdminUserResponse>> getUserById(@PathVariable UUID id) {
        AdminUserResponse user = adminUserService.getUserById(id);
        return ResponseEntity.ok(
                BaseResponse.<AdminUserResponse>builder()
                        .success(true)
                        .data(user)
                        .build()
        );
    }

    /**
     * PUT /admin/users/{id}/ban
     * Ban a user (set status to INACTIVE).
     */
    @PutMapping("/{id}/ban")
    @Operation(summary = "Ban user", description = "Ban a user account, preventing them from logging in")
    public ResponseEntity<BaseResponse<AdminUserResponse>> banUser(@PathVariable UUID id) {
        AdminUserResponse user = adminUserService.banUser(id);
        return ResponseEntity.ok(
                BaseResponse.<AdminUserResponse>builder()
                        .success(true)
                        .message("User has been banned successfully.")
                        .data(user)
                        .build()
        );
    }

    /**
     * PUT /admin/users/{id}/unban
     * Unban a user (set status back to ACTIVE).
     */
    @PutMapping("/{id}/unban")
    @Operation(summary = "Unban user", description = "Unban a user account, restoring their access")
    public ResponseEntity<BaseResponse<AdminUserResponse>> unbanUser(@PathVariable UUID id) {
        AdminUserResponse user = adminUserService.unbanUser(id);
        return ResponseEntity.ok(
                BaseResponse.<AdminUserResponse>builder()
                        .success(true)
                        .message("User has been unbanned successfully.")
                        .data(user)
                        .build()
        );
    }

    /**
     * POST /admin/users/{id}/reset-password
     * Admin-initiated password reset to the system default.
     */
    @PostMapping("/{id}/reset-password")
    @Operation(summary = "Reset user password", description = "Reset a user's password to the admin default password")
    public ResponseEntity<BaseResponse<String>> resetUserPassword(@PathVariable UUID id) {
        adminUserService.resetUserPasswordToDefault(id);
        return ResponseEntity.ok(
                BaseResponse.<String>builder()
                        .success(true)
                        .message("User password has been reset to the default password.")
                        .build()
        );
    }

    /**
     * PUT /admin/users/{id}
     * Update a user's details (fullName and role).
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update user details", description = "Update a user's full name and role (Admin only)")
    public ResponseEntity<BaseResponse<AdminUserResponse>> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody com.sep.comiverse.dto.request.AdminUpdateUserRequest request
    ) {
        AdminUserResponse user = adminUserService.updateUser(id, request);
        return ResponseEntity.ok(
                BaseResponse.<AdminUserResponse>builder()
                        .success(true)
                        .message("User details updated successfully.")
                        .data(user)
                        .build()
        );
    }
}
