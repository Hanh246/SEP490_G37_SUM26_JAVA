package com.sep.comiverse.controller;

import com.sep.comiverse.dto.request.RejectAuthorLicenseRequest;
import com.sep.comiverse.dto.request.ReopenAuthorLicenseRequest;
import com.sep.comiverse.dto.response.AuthorLicenseResponse;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.entity.enums.AuthorLicenseStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.AuthorLicenseService;
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
@RequestMapping("/author-license-reviews")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ADMIN','MODERATOR')")
@Tag(name = "Author License Review", description = "Admin/Moderator review APIs for Author license PDFs")
public class AuthorLicenseReviewController {

    private final AuthorLicenseService authorLicenseService;

    @GetMapping
    @Operation(summary = "List author license review items")
    public ResponseEntity<BaseResponse<List<AuthorLicenseResponse>>> list(
            @RequestParam(value = "status", required = false) AuthorLicenseStatus status
    ) {
        return ResponseEntity.ok(BaseResponse.<List<AuthorLicenseResponse>>builder()
                .success(true)
                .data(authorLicenseService.listReviewItems(status))
                .build());
    }

    @PostMapping("/{authorId}/approve")
    @Operation(summary = "Verify an Author license and activate publishing")
    public ResponseEntity<BaseResponse<AuthorLicenseResponse>> approve(
            @PathVariable UUID authorId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID reviewerId = requirePrincipal(principal);
        return ResponseEntity.ok(BaseResponse.<AuthorLicenseResponse>builder()
                .success(true)
                .message("License verified. Author is now ACTIVE.")
                .data(authorLicenseService.approve(authorId, reviewerId))
                .build());
    }

    @PostMapping("/{authorId}/reject")
    @Operation(summary = "Reject an Author license and grant a replacement-upload deadline")
    public ResponseEntity<BaseResponse<AuthorLicenseResponse>> reject(
            @PathVariable UUID authorId,
            @Valid @RequestBody(required = false) RejectAuthorLicenseRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID reviewerId = requirePrincipal(principal);
        String reason = request == null ? null : request.getReason();
        Integer deadlineDays = request == null ? null : request.getDeadlineDays();
        return ResponseEntity.ok(BaseResponse.<AuthorLicenseResponse>builder()
                .success(true)
                .message("License rejected. A new upload deadline has been assigned.")
                .data(authorLicenseService.reject(authorId, reviewerId, reason, deadlineDays))
                .build());
    }

    @PostMapping("/{authorId}/disable")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Disable Author publishing privileges")
    public ResponseEntity<BaseResponse<AuthorLicenseResponse>> disable(
            @PathVariable UUID authorId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID reviewerId = requirePrincipal(principal);
        return ResponseEntity.ok(BaseResponse.<AuthorLicenseResponse>builder()
                .success(true)
                .message("Author publishing privileges disabled.")
                .data(authorLicenseService.disable(authorId, reviewerId))
                .build());
    }

    @PostMapping("/{authorId}/reopen")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Give an expired/disabled Author a new PDF upload deadline")
    public ResponseEntity<BaseResponse<AuthorLicenseResponse>> reopen(
            @PathVariable UUID authorId,
            @Valid @RequestBody(required = false) ReopenAuthorLicenseRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID reviewerId = requirePrincipal(principal);
        Integer deadlineDays = request == null ? null : request.getDeadlineDays();
        return ResponseEntity.ok(BaseResponse.<AuthorLicenseResponse>builder()
                .success(true)
                .message("New Author license upload deadline assigned.")
                .data(authorLicenseService.reopen(authorId, reviewerId, deadlineDays))
                .build());
    }

    private UUID requirePrincipal(UserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new CustomException(401, "Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        return principal.getId();
    }
}
