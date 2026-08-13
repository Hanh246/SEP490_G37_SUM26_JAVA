package com.sep.comiverse.dto.response;

import com.sep.comiverse.entity.enums.AuthorLicenseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorLicenseResponse {
    private UUID authorId;
    private UUID userId;
    private String username;
    private String fullName;
    private String email;
    private AuthorLicenseStatus status;
    private String licenseUrl;
    private String licenseOriginalFilename;
    private Instant licenseDeadlineAt;
    private Instant licenseUploadedAt;
    private Instant licenseVerifiedAt;
    private Instant licenseReviewedAt;
    private UUID licenseReviewedById;
    private String licenseRejectionReason;
    private boolean canUploadLicense;
    private boolean canPublishComic;
    private boolean canRequestAuthorPayout;
}
