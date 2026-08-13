package com.sep.comiverse.controller;

import com.sep.comiverse.dto.request.AuthorProfileRequest;
import com.sep.comiverse.dto.response.AuthorProfileResponse;
import com.sep.comiverse.dto.response.AuthorLicenseResponse;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.AuthorProfileService;
import com.sep.comiverse.service.AuthorLicenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/author/profile")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('AUTHOR')")
@Tag(name = "Author - Profile", description = "APIs for author public/profile information")
public class AuthorProfileController {

    private final AuthorProfileService authorProfileService;
    private final AuthorLicenseService authorLicenseService;

    @GetMapping
    @Operation(summary = "Get my author profile", description = "Returns the profile owned by the authenticated author. A default profile is created if missing.")
    public ResponseEntity<BaseResponse<AuthorProfileResponse>> getMyProfile(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new CustomException(401, "Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        return ResponseEntity.ok(BaseResponse.<AuthorProfileResponse>builder()
                .success(true)
                .data(authorProfileService.getMyProfile(principal.getId()))
                .build());
    }

    @PostMapping(value = "/license", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload Author license PDF", description = "Accepts PDF only. Successful upload moves the Author to PENDING_VERIFICATION.")
    public ResponseEntity<BaseResponse<AuthorLicenseResponse>> uploadLicense(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new CustomException(401, "Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        return ResponseEntity.ok(BaseResponse.<AuthorLicenseResponse>builder()
                .success(true)
                .message("License PDF uploaded. Waiting for verification.")
                .data(authorLicenseService.uploadLicense(principal.getId(), file))
                .build());
    }

    @PostMapping
    @Operation(summary = "Create or update my author profile", description = "Creates or updates author profile information for the authenticated author")
    public ResponseEntity<BaseResponse<AuthorProfileResponse>> upsertMyProfile(
            @Valid @RequestBody AuthorProfileRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new CustomException(401, "Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        return ResponseEntity.ok(BaseResponse.<AuthorProfileResponse>builder()
                .success(true)
                .message("Author profile saved")
                .data(authorProfileService.updateMyProfile(principal.getId(), request))
                .build());
    }

    @PutMapping
    @Operation(summary = "Update my author profile", description = "Updates author profile information for the authenticated author")
    public ResponseEntity<BaseResponse<AuthorProfileResponse>> updateMyProfile(
            @Valid @RequestBody AuthorProfileRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return upsertMyProfile(request, principal);
    }
}
