package com.sep.comiverse.entity.enums;

/**
 * Lifecycle of an Author account's publishing license.
 *
 * PENDING_LICENSE      - account can login/edit profile/upload a PDF license only.
 * PENDING_VERIFICATION - PDF was uploaded and is waiting for Admin/Moderator review.
 * ACTIVE               - license verified; comic publishing and Author payout are enabled.
 * REJECTED             - license was rejected; Author may upload a replacement before the new deadline.
 * EXPIRED              - upload/re-upload deadline passed before a valid PDF was submitted.
 * AUTHOR_DISABLED      - publishing privileges manually disabled by an administrator.
 */
public enum AuthorLicenseStatus {
    PENDING_LICENSE,
    PENDING_VERIFICATION,
    ACTIVE,
    REJECTED,
    EXPIRED,
    AUTHOR_DISABLED
}
