package com.sep.comiverse.service;

import com.sep.comiverse.dto.response.AuthorLicenseResponse;
import com.sep.comiverse.entity.AuthorEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.AuthorLicenseStatus;
import com.sep.comiverse.entity.enums.AuthorType;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IAuthorRepository;
import com.sep.comiverse.repository.IUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthorLicenseService {

    public static final int DEFAULT_LICENSE_DEADLINE_DAYS = 7;
    public static final long MAX_LICENSE_PDF_SIZE_BYTES = 10L * 1024L * 1024L;

    private final IAuthorRepository authorRepository;
    private final IUserRepository userRepository;
    private final CloudinaryStorageService cloudinaryStorageService;

    /**
     * Creates the author profile used by an Admin-created AUTHOR account.
     * New Admin-created Authors always start at PENDING_LICENSE with a 7-day deadline.
     */
    @Transactional
    public AuthorEntity initializePendingLicenseAuthor(UserEntity user, UUID createdByAdminId) {
        if (user == null || user.getId() == null) {
            throw new CustomException(400, "Author user is required", HttpStatus.BAD_REQUEST);
        }

        return authorRepository.findByUserIdAndDeletedFalse(user.getId())
                .orElseGet(() -> {
                    UserEntity admin = createdByAdminId == null
                            ? null
                            : userRepository.findById(createdByAdminId).orElse(null);
                    AuthorEntity author = AuthorEntity.builder()
                            .user(user)
                            .createdByAdmin(admin)
                            .authorType(AuthorType.INDIVIDUAL)
                            .displayName(firstNonBlank(user.getFullName(), user.getUsername(), user.getEmail(), "Author"))
                            .legalName(user.getFullName())
                            .avatarUrl(user.getAvatarUrl())
                            .contactEmail(user.getEmail())
                            .licenseStatus(AuthorLicenseStatus.PENDING_LICENSE)
                            .licenseDeadlineAt(Instant.now().plus(Duration.ofDays(DEFAULT_LICENSE_DEADLINE_DAYS)))
                            .build();
                    return authorRepository.save(author);
                });
    }

    @Transactional
    public AuthorLicenseResponse getMyLicense(UUID userId) {
        AuthorEntity author = requireAuthor(userId);
        refreshExpiry(author);
        return toResponse(author);
    }

    @Transactional
    public AuthorLicenseResponse uploadLicense(UUID userId, MultipartFile file) {
        AuthorEntity author = requireAuthor(userId);
        refreshExpiry(author);

        AuthorLicenseStatus status = effectiveStatus(author);
        if (status != AuthorLicenseStatus.PENDING_LICENSE && status != AuthorLicenseStatus.REJECTED) {
            throw new CustomException(
                    409,
                    switch (status) {
                        case PENDING_VERIFICATION -> "License is already waiting for verification";
                        case ACTIVE -> "Author license is already verified";
                        case EXPIRED -> "License upload deadline has expired. Contact an administrator for a new deadline";
                        case AUTHOR_DISABLED -> "Author publishing privileges are disabled";
                        default -> "License cannot be uploaded while status is " + status;
                    },
                    HttpStatus.CONFLICT
            );
        }

        if (author.getLicenseDeadlineAt() == null || !author.getLicenseDeadlineAt().isAfter(Instant.now())) {
            author.setLicenseStatus(AuthorLicenseStatus.EXPIRED);
            authorRepository.save(author);
            throw new CustomException(403, "License upload deadline has expired", HttpStatus.FORBIDDEN);
        }

        validatePdf(file);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new CustomException(400, "Cannot read uploaded PDF", HttpStatus.BAD_REQUEST);
        }
        validatePdfSignature(bytes);

        CloudinaryUploadResult upload = cloudinaryStorageService.uploadRawFile(
                bytes,
                file.getOriginalFilename(),
                "comiverse/author-licenses/" + author.getId()
        );

        Instant now = Instant.now();
        author.setLicenseUrl(upload.getSecureUrl());
        author.setLicenseOriginalFilename(safeFilename(file.getOriginalFilename()));
        author.setLicenseUploadedAt(now);
        author.setLicenseStatus(AuthorLicenseStatus.PENDING_VERIFICATION);
        author.setLicenseRejectionReason(null);
        author.setLicenseReviewedAt(null);
        author.setLicenseReviewedById(null);
        author.setLicenseVerifiedAt(null);
        return toResponse(authorRepository.save(author));
    }

    @Transactional(readOnly = true)
    public List<AuthorLicenseResponse> listReviewItems(AuthorLicenseStatus status) {
        return authorRepository.findLicenseReviewItems(status)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AuthorLicenseResponse approve(UUID authorId, UUID reviewerUserId) {
        AuthorEntity author = requireAuthorById(authorId);
        refreshExpiry(author);
        if (effectiveStatus(author) != AuthorLicenseStatus.PENDING_VERIFICATION) {
            throw new CustomException(409, "Only PENDING_VERIFICATION licenses can be approved", HttpStatus.CONFLICT);
        }
        if (!StringUtils.hasText(author.getLicenseUrl())) {
            throw new CustomException(409, "Author has not uploaded a license PDF", HttpStatus.CONFLICT);
        }

        Instant now = Instant.now();
        author.setLicenseStatus(AuthorLicenseStatus.ACTIVE);
        author.setLicenseVerifiedAt(now);
        author.setLicenseReviewedAt(now);
        author.setLicenseReviewedById(reviewerUserId);
        author.setLicenseRejectionReason(null);
        return toResponse(authorRepository.save(author));
    }

    @Transactional
    public AuthorLicenseResponse reject(UUID authorId, UUID reviewerUserId, String reason, Integer deadlineDays) {
        AuthorEntity author = requireAuthorById(authorId);
        refreshExpiry(author);
        if (effectiveStatus(author) != AuthorLicenseStatus.PENDING_VERIFICATION) {
            throw new CustomException(409, "Only PENDING_VERIFICATION licenses can be rejected", HttpStatus.CONFLICT);
        }

        String rejectionReason = trimToNull(reason);
        if (rejectionReason == null) {
            throw new CustomException(400, "Rejection reason is required", HttpStatus.BAD_REQUEST);
        }

        int days = normalizeDeadlineDays(deadlineDays);
        Instant now = Instant.now();
        author.setLicenseStatus(AuthorLicenseStatus.REJECTED);
        author.setLicenseRejectionReason(rejectionReason);
        author.setLicenseReviewedAt(now);
        author.setLicenseReviewedById(reviewerUserId);
        author.setLicenseVerifiedAt(null);
        author.setLicenseDeadlineAt(now.plus(Duration.ofDays(days)));
        return toResponse(authorRepository.save(author));
    }

    @Transactional
    public AuthorLicenseResponse reopen(UUID authorId, UUID reviewerUserId, Integer deadlineDays) {
        AuthorEntity author = requireAuthorById(authorId);
        AuthorLicenseStatus status = effectiveStatus(author);
        if (status != AuthorLicenseStatus.EXPIRED && status != AuthorLicenseStatus.AUTHOR_DISABLED) {
            throw new CustomException(409, "Only EXPIRED or AUTHOR_DISABLED Authors can receive a new upload deadline", HttpStatus.CONFLICT);
        }

        int days = normalizeDeadlineDays(deadlineDays);
        Instant now = Instant.now();
        author.setLicenseStatus(AuthorLicenseStatus.PENDING_LICENSE);
        author.setLicenseDeadlineAt(now.plus(Duration.ofDays(days)));
        author.setLicenseReviewedAt(now);
        author.setLicenseReviewedById(reviewerUserId);
        author.setLicenseRejectionReason(null);
        return toResponse(authorRepository.save(author));
    }

    @Transactional
    public AuthorLicenseResponse disable(UUID authorId, UUID reviewerUserId) {
        AuthorEntity author = requireAuthorById(authorId);
        author.setLicenseStatus(AuthorLicenseStatus.AUTHOR_DISABLED);
        author.setLicenseReviewedAt(Instant.now());
        author.setLicenseReviewedById(reviewerUserId);
        return toResponse(authorRepository.save(author));
    }

    @Transactional
    public void assertPublishingAllowed(UUID userId) {
        AuthorEntity author = requireAuthor(userId);
        refreshExpiry(author);
        AuthorLicenseStatus status = effectiveStatus(author);
        if (status != AuthorLicenseStatus.ACTIVE) {
            throw new CustomException(
                    403,
                    "Author license must be ACTIVE before creating, uploading, or submitting comics. Current status: " + status,
                    HttpStatus.FORBIDDEN
            );
        }
    }

    @Transactional
    public void assertAuthorPayoutAllowed(UUID userId) {
        AuthorEntity author = requireAuthor(userId);
        refreshExpiry(author);
        AuthorLicenseStatus status = effectiveStatus(author);
        if (status != AuthorLicenseStatus.ACTIVE) {
            throw new CustomException(
                    403,
                    "Author payout is disabled until the license is verified. Current status: " + status,
                    HttpStatus.FORBIDDEN
            );
        }
    }

    @Transactional
    public boolean isAuthorPayoutAllowed(UUID userId) {
        AuthorEntity author = requireAuthor(userId);
        refreshExpiry(author);
        return effectiveStatus(author) == AuthorLicenseStatus.ACTIVE;
    }

    @Transactional
    public int expireOverdueLicenses() {
        Instant now = Instant.now();
        List<AuthorEntity> candidates = authorRepository.findExpiredLicenseCandidates(
                List.of(AuthorLicenseStatus.PENDING_LICENSE, AuthorLicenseStatus.REJECTED),
                now
        );
        candidates.forEach(author -> author.setLicenseStatus(AuthorLicenseStatus.EXPIRED));
        if (!candidates.isEmpty()) {
            authorRepository.saveAll(candidates);
        }
        return candidates.size();
    }

    /**
     * Fail closed for legacy/incomplete rows. A missing license status must never
     * silently grant publishing or payout privileges.
     */
    public AuthorLicenseStatus effectiveStatus(AuthorEntity author) {
        if (author == null || author.getLicenseStatus() == null) {
            return AuthorLicenseStatus.PENDING_LICENSE;
        }
        return author.getLicenseStatus();
    }

    private AuthorEntity requireAuthor(UUID userId) {
        if (userId == null) {
            throw new CustomException(401, "Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        return authorRepository.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new CustomException(404, "Author profile not found", HttpStatus.NOT_FOUND));
    }

    private AuthorEntity requireAuthorById(UUID authorId) {
        if (authorId == null) {
            throw new CustomException(400, "Author id is required", HttpStatus.BAD_REQUEST);
        }
        AuthorEntity author = authorRepository.findById(authorId)
                .orElseThrow(() -> new CustomException(404, "Author profile not found", HttpStatus.NOT_FOUND));
        if (Boolean.TRUE.equals(author.getDeleted())) {
            throw new CustomException(404, "Author profile not found", HttpStatus.NOT_FOUND);
        }
        return author;
    }

    private void refreshExpiry(AuthorEntity author) {
        Instant now = Instant.now();

        // Repair legacy/incomplete rows on access instead of treating NULL as ACTIVE.
        if (author.getLicenseStatus() == null) {
            author.setLicenseStatus(AuthorLicenseStatus.PENDING_LICENSE);
            if (author.getLicenseDeadlineAt() == null) {
                author.setLicenseDeadlineAt(now.plus(Duration.ofDays(DEFAULT_LICENSE_DEADLINE_DAYS)));
            }
            authorRepository.save(author);
            return;
        }

        AuthorLicenseStatus status = author.getLicenseStatus();
        if (status == AuthorLicenseStatus.PENDING_LICENSE && author.getLicenseDeadlineAt() == null) {
            author.setLicenseDeadlineAt(now.plus(Duration.ofDays(DEFAULT_LICENSE_DEADLINE_DAYS)));
            authorRepository.save(author);
            return;
        }

        if ((status == AuthorLicenseStatus.PENDING_LICENSE || status == AuthorLicenseStatus.REJECTED)
                && author.getLicenseDeadlineAt() != null
                && !author.getLicenseDeadlineAt().isAfter(now)) {
            author.setLicenseStatus(AuthorLicenseStatus.EXPIRED);
            authorRepository.save(author);
        }
    }

    private AuthorLicenseResponse toResponse(AuthorEntity author) {
        UserEntity user = author.getUser();
        AuthorLicenseStatus status = effectiveStatus(author);
        boolean canUpload = (status == AuthorLicenseStatus.PENDING_LICENSE || status == AuthorLicenseStatus.REJECTED)
                && author.getLicenseDeadlineAt() != null
                && author.getLicenseDeadlineAt().isAfter(Instant.now());
        boolean active = status == AuthorLicenseStatus.ACTIVE;

        return AuthorLicenseResponse.builder()
                .authorId(author.getId())
                .userId(user == null ? null : user.getId())
                .username(user == null ? null : user.getUsername())
                .fullName(user == null ? null : user.getFullName())
                .email(user == null ? null : user.getEmail())
                .status(status)
                .licenseUrl(author.getLicenseUrl())
                .licenseOriginalFilename(author.getLicenseOriginalFilename())
                .licenseDeadlineAt(author.getLicenseDeadlineAt())
                .licenseUploadedAt(author.getLicenseUploadedAt())
                .licenseVerifiedAt(author.getLicenseVerifiedAt())
                .licenseReviewedAt(author.getLicenseReviewedAt())
                .licenseReviewedById(author.getLicenseReviewedById())
                .licenseRejectionReason(author.getLicenseRejectionReason())
                .canUploadLicense(canUpload)
                .canPublishComic(active)
                .canRequestAuthorPayout(active)
                .build();
    }

    private void validatePdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(400, "License PDF is required", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > MAX_LICENSE_PDF_SIZE_BYTES) {
            throw new CustomException(400, "License PDF must not exceed 10 MB", HttpStatus.BAD_REQUEST);
        }
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename) || !filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new CustomException(400, "Only .pdf license files are accepted", HttpStatus.BAD_REQUEST);
        }
        if (!"application/pdf".equalsIgnoreCase(file.getContentType())) {
            throw new CustomException(400, "License file MIME type must be application/pdf", HttpStatus.BAD_REQUEST);
        }
    }

    private void validatePdfSignature(byte[] bytes) {
        if (bytes == null || bytes.length < 5
                || bytes[0] != '%'
                || bytes[1] != 'P'
                || bytes[2] != 'D'
                || bytes[3] != 'F'
                || bytes[4] != '-') {
            throw new CustomException(400, "Uploaded file is not a valid PDF document", HttpStatus.BAD_REQUEST);
        }
    }

    private int normalizeDeadlineDays(Integer deadlineDays) {
        int days = deadlineDays == null ? DEFAULT_LICENSE_DEADLINE_DAYS : deadlineDays;
        if (days < 1 || days > 30) {
            throw new CustomException(400, "Deadline must be between 1 and 30 days", HttpStatus.BAD_REQUEST);
        }
        return days;
    }

    private String safeFilename(String filename) {
        if (!StringUtils.hasText(filename)) return "license.pdf";
        String value = filename.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) value = value.substring(slash + 1);
        return value.length() > 255 ? value.substring(value.length() - 255) : value;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (StringUtils.hasText(value)) return value.trim();
        }
        return null;
    }
}
