package com.sep.comiverse.service;

import com.sep.comiverse.dto.response.OfflineLicenseResponse;
import com.sep.comiverse.config.OfflineDownloadProperties;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.OfflineDeviceEntity;
import com.sep.comiverse.entity.OfflinePackageEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.ChapterStatus;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.exception.OfflineDownloadException;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IOfflinePackageRepository;
import com.sep.comiverse.repository.IUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class OfflineChapterDownloadService {

    private final IUserRepository userRepository;
    private final IChapterRepository chapterRepository;
    private final IOfflinePackageRepository packageRepository;
    private final PremiumPlanService premiumPlanService;
    private final OfflineDeviceService deviceService;
    private final OfflineChapterPackageService packageService;
    private final OfflineLicenseSigner licenseSigner;
    private final OfflineDownloadProperties properties;
    private final OfflineDownloadCryptoService cryptoService;

    public OfflineChapterDownloadService(
            IUserRepository userRepository,
            IChapterRepository chapterRepository,
            IOfflinePackageRepository packageRepository,
            PremiumPlanService premiumPlanService,
            OfflineDeviceService deviceService,
            OfflineChapterPackageService packageService,
            OfflineLicenseSigner licenseSigner,
            OfflineDownloadProperties properties,
            OfflineDownloadCryptoService cryptoService
    ) {
        this.userRepository = userRepository;
        this.chapterRepository = chapterRepository;
        this.packageRepository = packageRepository;
        this.premiumPlanService = premiumPlanService;
        this.deviceService = deviceService;
        this.packageService = packageService;
        this.licenseSigner = licenseSigner;
        this.properties = properties;
        this.cryptoService = cryptoService;
    }

    public ProtectedChapterPackage createPackage(UUID userId, UUID chapterId, UUID deviceKeyId) {
        return createPackage(userId, chapterId, deviceKeyId, false);
    }

    public ProtectedChapterPackage createPackage(
            UUID userId,
            UUID chapterId,
            UUID deviceKeyId,
            boolean includeTranslations
    ) {
        Instant serverTime = Instant.now();
        requireEligiblePremiumReader(userId);
        ChapterEntity chapter = requirePublishedChapter(chapterId);
        OfflineDeviceEntity device = deviceService.requireActiveDevice(userId, deviceKeyId);

        OfflineChapterPackageService.PreparedPackage prepared = packageService.create(
                chapter,
                device,
                serverTime,
                includeTranslations
        );
        try {
            requireEligiblePremiumReader(userId);
            ChapterEntity currentChapter = requirePublishedChapter(chapterId);
            String currentSourceDescriptor = includeTranslations
                    ? packageService.sourceDescriptor(currentChapter, true)
                    : packageService.sourceDescriptor(currentChapter);
            if (!prepared.record().getSourceDescriptorSha256().equals(currentSourceDescriptor)) {
                throw new OfflineDownloadException(
                        "CONTENT_CHANGED_RETRY",
                        "Chapter content changed while the offline package was being created. Please retry",
                        HttpStatus.CONFLICT
                );
            }
            OfflineDeviceEntity currentDevice = deviceService.requireActiveDevice(userId, deviceKeyId);
            OfflineLicenseSigner.SignedOfflineLicense license = licenseSigner.issue(
                    prepared.record(),
                    currentDevice,
                    Instant.now()
            );
            Instant completedAt = license.issuedAt();
            return new ProtectedChapterPackage(
                    prepared.record(),
                    prepared.artifact(),
                    license,
                    completedAt,
                    toLicenseResponse(prepared.record(), currentDevice, license, completedAt)
            );
        } catch (RuntimeException exception) {
            prepared.record().setRevoked(true);
            packageRepository.save(prepared.record());
            prepared.artifact().close();
            throw exception;
        }
    }

    @Transactional
    public OfflineLicenseResponse renewLicense(
            UUID userId,
            UUID packageId,
            UUID deviceKeyId
    ) {
        Instant serverTime = Instant.now();
        requireEligiblePremiumReader(userId);
        OfflineDeviceEntity device = deviceService.requireActiveDevice(userId, deviceKeyId);
        OfflinePackageEntity offlinePackage = packageRepository
                .findByPackageIdAndUserIdAndRevokedFalseAndDeletedFalse(packageId, userId)
                .orElseThrow(() -> new OfflineDownloadException(
                        "OFFLINE_PACKAGE_NOT_FOUND",
                        "The offline package is unavailable or has been revoked",
                        HttpStatus.NOT_FOUND
                ));
        if (!device.getId().equals(offlinePackage.getOfflineDeviceId())
                || !device.getPublicKeySha256().equalsIgnoreCase(offlinePackage.getDeviceKeySha256())) {
            throw new OfflineDownloadException(
                    "OFFLINE_PACKAGE_DEVICE_MISMATCH",
                    "The offline package belongs to a different device key",
                    HttpStatus.FORBIDDEN
            );
        }

        ChapterEntity chapter = requirePublishedChapter(offlinePackage.getChapterId());
        String currentSourceDescriptor = offlinePackage.getFormatVersion() != null
                && offlinePackage.getFormatVersion() >= OfflineChapterPackageService.TRANSLATED_FORMAT_VERSION
                ? packageService.sourceDescriptor(chapter, true)
                : packageService.sourceDescriptor(chapter);
        if (!currentSourceDescriptor.equals(offlinePackage.getSourceDescriptorSha256())) {
            offlinePackage.setRevoked(true);
            packageRepository.save(offlinePackage);
            throw new OfflineDownloadException(
                    "OFFLINE_PACKAGE_OUTDATED",
                    "The chapter was updated and must be downloaded again",
                    HttpStatus.CONFLICT
            );
        }

        OfflineLicenseSigner.SignedOfflineLicense license = licenseSigner.issue(
                offlinePackage,
                device,
                serverTime
        );
        return toLicenseResponse(offlinePackage, device, license, serverTime);
    }

    private UserEntity requireEligiblePremiumReader(UUID userId) {
        UserEntity user = userRepository.findByIdWithRole(userId)
                .orElseThrow(() -> new OfflineDownloadException(
                        "USER_NOT_FOUND",
                        "The signed-in account no longer exists",
                        HttpStatus.NOT_FOUND
                ));
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new OfflineDownloadException(
                    "ACCOUNT_NOT_ACTIVE",
                    "The account is not active",
                    HttpStatus.FORBIDDEN
            );
        }
        String role = user.getRole() == null ? "" : user.getRole().getRoleName();
        if (!"READER".equalsIgnoreCase(role)) {
            throw new OfflineDownloadException(
                    "READER_ACCOUNT_REQUIRED",
                    "Offline chapter downloads are available to Reader accounts only",
                    HttpStatus.FORBIDDEN
            );
        }
        if (!premiumPlanService.hasActivePremium(user)) {
            throw new OfflineDownloadException(
                    "PREMIUM_REQUIRED",
                    "An active Premium subscription is required for offline downloads",
                    HttpStatus.FORBIDDEN
            );
        }
        return user;
    }

    private ChapterEntity requirePublishedChapter(UUID chapterId) {
        ChapterEntity chapter = chapterRepository
                .findForOfflineDownload(chapterId, ChapterStatus.PUBLISHED)
                .orElseThrow(() -> new OfflineDownloadException(
                        "CHAPTER_NOT_FOUND",
                        "The published chapter was not found",
                        HttpStatus.NOT_FOUND
                ));
        if (chapter.getComic() == null
                || chapter.getComic().getModerationStatus() != ComicModerationStatus.PUBLISHED) {
            throw new OfflineDownloadException(
                    "CHAPTER_NOT_AVAILABLE",
                    "The chapter is not currently available for readers",
                    HttpStatus.NOT_FOUND
            );
        }
        return chapter;
    }

    public OfflineLicenseResponse toLicenseResponse(
            OfflinePackageEntity offlinePackage,
            OfflineDeviceEntity device,
            OfflineLicenseSigner.SignedOfflineLicense license,
            Instant serverTime
    ) {
        return OfflineLicenseResponse.builder()
                .packageId(offlinePackage.getPackageId())
                .chapterId(offlinePackage.getChapterId())
                .comicId(offlinePackage.getComicId())
                .deviceKeyId(device.getId())
                .licenseToken(license.token())
                .wrappedContentKey(offlinePackage.getWrappedContentKey())
                .keyAlgorithm(offlinePackage.getKeyAlgorithm())
                .signingKeyId(properties.getSigningKeyId())
                .packageSha256(offlinePackage.getPackageSha256())
                .manifestSha256(offlinePackage.getManifestSha256())
                .contentRevision(offlinePackage.getContentRevision())
                .deviceIdHash(device.getDeviceIdHash())
                .deviceKeySha256(device.getPublicKeySha256())
                .wrappedKeySha256(cryptoService.sha256Hex(
                        cryptoService.decodeFlexible(offlinePackage.getWrappedContentKey())
                ))
                .packageSize(offlinePackage.getPackageSize())
                .formatVersion(offlinePackage.getFormatVersion())
                .offlineUntil(license.expiresAt())
                .renewAfter(license.expiresAt().minus(java.time.Duration.ofHours(24)))
                .serverTime(serverTime)
                .build();
    }

    public record ProtectedChapterPackage(
            OfflinePackageEntity record,
            OfflineChapterPackageService.OfflinePackageArtifact artifact,
            OfflineLicenseSigner.SignedOfflineLicense license,
            Instant serverTime,
            OfflineLicenseResponse metadata
    ) {
    }
}
