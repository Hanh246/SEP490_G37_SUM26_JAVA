package com.sep.comiverse.service;

import com.sep.comiverse.dto.request.AuthorProfileRequest;
import com.sep.comiverse.dto.response.AuthorProfileResponse;
import com.sep.comiverse.entity.AuthorEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.AuthorType;
import com.sep.comiverse.entity.enums.AuthorLicenseStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IAuthorRepository;
import com.sep.comiverse.repository.IUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthorProfileService {

    private final IAuthorRepository authorRepository;
    private final IUserRepository userRepository;
    private final AuthorLicenseService authorLicenseService;

    @Transactional
    public AuthorProfileResponse getMyProfile(UUID userId) {
        AuthorEntity author = getOrCreateAuthorProfile(userId);
        authorLicenseService.getMyLicense(userId); // also applies deadline expiry synchronously
        return toResponse(author);
    }

    @Transactional
    public AuthorProfileResponse updateMyProfile(UUID userId, AuthorProfileRequest request) {
        if (request == null) {
            throw new CustomException(400, "Author profile payload is required", HttpStatus.BAD_REQUEST);
        }

        AuthorEntity author = getOrCreateAuthorProfile(userId);
        author.setAuthorType(request.getAuthorType() != null ? request.getAuthorType() : AuthorType.INDIVIDUAL);
        author.setDisplayName(requiredTrim(request.getDisplayName(), "Display name is required"));
        author.setLegalName(trimToNull(request.getLegalName()));
        author.setBio(trimToNull(request.getBio()));
        author.setAvatarUrl(trimToNull(request.getAvatarUrl()));
        author.setContactEmail(firstNonBlank(request.getContactEmail(), author.getUser().getEmail()));
        author.setExternalProfileRef(trimToNull(request.getExternalProfileRef()));
        author.setNote(trimToNull(request.getNote()));

        AuthorEntity saved = authorRepository.save(author);
        authorLicenseService.getMyLicense(userId); // keep status/deadline current
        return toResponse(saved);
    }

    private AuthorEntity getOrCreateAuthorProfile(UUID userId) {
        if (userId == null) {
            throw new CustomException(401, "Unauthorized", HttpStatus.UNAUTHORIZED);
        }

        return authorRepository.findByUserIdAndDeletedFalse(userId)
                .orElseGet(() -> createDefaultProfile(userId));
    }

    private AuthorEntity createDefaultProfile(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(404, "User not found", HttpStatus.NOT_FOUND));

        String displayName = firstNonBlank(user.getFullName(), user.getUsername(), user.getEmail(), "Author");
        AuthorEntity author = AuthorEntity.builder()
                .user(user)
                .authorType(AuthorType.INDIVIDUAL)
                .displayName(displayName)
                .legalName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .contactEmail(user.getEmail())
                // Fail closed: an AUTHOR profile created through any path must complete
                // license verification before publishing or receiving Author payout.
                .licenseStatus(AuthorLicenseStatus.PENDING_LICENSE)
                .licenseDeadlineAt(Instant.now().plus(Duration.ofDays(AuthorLicenseService.DEFAULT_LICENSE_DEADLINE_DAYS)))
                .build();
        return authorRepository.save(author);
    }

    private AuthorProfileResponse toResponse(AuthorEntity author) {
        UserEntity user = author.getUser();
        return AuthorProfileResponse.builder()
                .id(author.getId())
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .authorType(author.getAuthorType())
                .displayName(author.getDisplayName())
                .legalName(author.getLegalName())
                .bio(author.getBio())
                .avatarUrl(author.getAvatarUrl())
                .contactEmail(author.getContactEmail())
                .externalProfileRef(author.getExternalProfileRef())
                .note(author.getNote())
                .licenseStatus(authorLicenseService.effectiveStatus(author))
                .licenseUrl(author.getLicenseUrl())
                .licenseOriginalFilename(author.getLicenseOriginalFilename())
                .licenseDeadlineAt(author.getLicenseDeadlineAt())
                .licenseUploadedAt(author.getLicenseUploadedAt())
                .licenseVerifiedAt(author.getLicenseVerifiedAt())
                .licenseRejectionReason(author.getLicenseRejectionReason())
                .canUploadLicense(canUploadLicense(author))
                .canPublishComic(authorLicenseService.effectiveStatus(author) == AuthorLicenseStatus.ACTIVE)
                .canRequestAuthorPayout(authorLicenseService.effectiveStatus(author) == AuthorLicenseStatus.ACTIVE)
                .build();
    }


    private boolean canUploadLicense(AuthorEntity author) {
        AuthorLicenseStatus status = authorLicenseService.effectiveStatus(author);
        return (status == AuthorLicenseStatus.PENDING_LICENSE || status == AuthorLicenseStatus.REJECTED)
                && author.getLicenseDeadlineAt() != null
                && author.getLicenseDeadlineAt().isAfter(Instant.now());
    }

    private String requiredTrim(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new CustomException(400, message, HttpStatus.BAD_REQUEST);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
