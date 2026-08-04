package com.sep.comiverse.unit.service;

import com.sep.comiverse.config.OfflineDownloadProperties;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.OfflineDeviceEntity;
import com.sep.comiverse.entity.OfflinePackageEntity;
import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.ChapterStatus;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.exception.OfflineDownloadException;
import com.sep.comiverse.repository.IChapterRepository;
import com.sep.comiverse.repository.IOfflinePackageRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.service.OfflineChapterDownloadService;
import com.sep.comiverse.service.OfflineChapterPackageService;
import com.sep.comiverse.service.OfflineDeviceService;
import com.sep.comiverse.service.OfflineDownloadCryptoService;
import com.sep.comiverse.service.OfflineLicenseSigner;
import com.sep.comiverse.service.PremiumPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfflineChapterDownloadServiceTest {

    private IUserRepository users;
    private IChapterRepository chapters;
    private IOfflinePackageRepository packages;
    private PremiumPlanService premium;
    private OfflineDeviceService devices;
    private OfflineChapterPackageService packageService;
    private OfflineLicenseSigner signer;
    private OfflineDownloadCryptoService crypto;
    private OfflineChapterDownloadService service;
    private UserEntity reader;
    private ChapterEntity chapter;
    private OfflineDeviceEntity device;
    private OfflinePackageEntity offlinePackage;

    @BeforeEach
    void setUp() {
        users = mock(IUserRepository.class);
        chapters = mock(IChapterRepository.class);
        packages = mock(IOfflinePackageRepository.class);
        premium = mock(PremiumPlanService.class);
        devices = mock(OfflineDeviceService.class);
        packageService = mock(OfflineChapterPackageService.class);
        signer = mock(OfflineLicenseSigner.class);
        crypto = mock(OfflineDownloadCryptoService.class);
        OfflineDownloadProperties properties = new OfflineDownloadProperties();
        service = new OfflineChapterDownloadService(
                users,
                chapters,
                packages,
                premium,
                devices,
                packageService,
                signer,
                properties,
                crypto
        );

        UUID userId = UUID.randomUUID();
        RoleEntity readerRole = RoleEntity.builder().roleName("READER").build();
        reader = UserEntity.builder()
                .email("reader@example.com")
                .status("ACTIVE")
                .role(readerRole)
                .build();
        reader.setId(userId);
        ComicEntity comic = ComicEntity.builder()
                .title("Published comic")
                .moderationStatus(ComicModerationStatus.PUBLISHED)
                .build();
        comic.setId(UUID.randomUUID());
        chapter = ChapterEntity.builder()
                .comic(comic)
                .moderationStatus(ChapterStatus.PUBLISHED)
                .images(List.of("https://res.cloudinary.com/demo/page.png"))
                .contentHash("legacy-hash-value")
                .build();
        chapter.setId(UUID.randomUUID());
        device = OfflineDeviceEntity.builder()
                .userId(userId)
                .deviceIdHash("a".repeat(64))
                .publicKeySha256("b".repeat(64))
                .publicKeyBase64("public-key")
                .revoked(false)
                .lastSeenAt(Instant.now())
                .build();
        device.setId(UUID.randomUUID());
        offlinePackage = OfflinePackageEntity.builder()
                .packageId(UUID.randomUUID())
                .userId(userId)
                .chapterId(chapter.getId())
                .comicId(comic.getId())
                .offlineDeviceId(device.getId())
                .deviceKeySha256(device.getPublicKeySha256())
                .contentRevision("c".repeat(64))
                .sourceDescriptorSha256("d".repeat(64))
                .manifestSha256("e".repeat(64))
                .packageSha256("f".repeat(64))
                .packageSize(100L)
                .wrappedContentKey("wrapped")
                .keyAlgorithm(OfflineDownloadCryptoService.KEY_WRAP_ALGORITHM)
                .formatVersion(1)
                .revoked(false)
                .build();
        when(users.findByIdWithRole(userId)).thenReturn(Optional.of(reader));
    }

    @Test
    void renewalFailsBeforePackageLookupWhenPremiumEnded() {
        when(premium.hasActivePremium(reader)).thenReturn(false);

        OfflineDownloadException exception = assertThrows(
                OfflineDownloadException.class,
                () -> service.renewLicense(reader.getId(), offlinePackage.getPackageId(), device.getId())
        );

        assertEquals("PREMIUM_REQUIRED", exception.getErrorCode());
        verify(packages, never()).findByPackageIdAndUserIdAndRevokedFalseAndDeletedFalse(any(), any());
    }

    @Test
    void renewalKeepsPackageAndWrappedKeyWhenAllBindingsRemainValid() {
        when(premium.hasActivePremium(reader)).thenReturn(true);
        when(devices.requireActiveDevice(reader.getId(), device.getId())).thenReturn(device);
        when(packages.findByPackageIdAndUserIdAndRevokedFalseAndDeletedFalse(
                offlinePackage.getPackageId(), reader.getId()
        )).thenReturn(Optional.of(offlinePackage));
        when(chapters.findForOfflineDownload(chapter.getId(), ChapterStatus.PUBLISHED))
                .thenReturn(Optional.of(chapter));
        when(packageService.sourceDescriptor(chapter)).thenReturn(offlinePackage.getSourceDescriptorSha256());
        when(crypto.decodeFlexible("wrapped")).thenReturn(new byte[]{1, 2, 3});
        when(crypto.sha256Hex(any(byte[].class))).thenReturn("wrapped-hash");
        when(signer.issue(any(), any(), any())).thenAnswer(invocation -> {
            Instant now = invocation.getArgument(2);
            return new OfflineLicenseSigner.SignedOfflineLicense(
                    "new.signed.license",
                    UUID.randomUUID(),
                    now,
                    now.plusSeconds(604800)
            );
        });

        var response = service.renewLicense(reader.getId(), offlinePackage.getPackageId(), device.getId());

        assertEquals(offlinePackage.getPackageId(), response.getPackageId());
        assertEquals("wrapped", response.getWrappedContentKey());
        assertEquals(offlinePackage.getPackageSha256(), response.getPackageSha256());
        assertEquals("new.signed.license", response.getLicenseToken());
    }

    @Test
    void changedChapterSourceRevokesPackageAndRequiresRedownload() {
        when(premium.hasActivePremium(reader)).thenReturn(true);
        when(devices.requireActiveDevice(reader.getId(), device.getId())).thenReturn(device);
        when(packages.findByPackageIdAndUserIdAndRevokedFalseAndDeletedFalse(
                offlinePackage.getPackageId(), reader.getId()
        )).thenReturn(Optional.of(offlinePackage));
        when(chapters.findForOfflineDownload(chapter.getId(), ChapterStatus.PUBLISHED))
                .thenReturn(Optional.of(chapter));
        when(packageService.sourceDescriptor(chapter)).thenReturn("new-source-descriptor");

        OfflineDownloadException exception = assertThrows(
                OfflineDownloadException.class,
                () -> service.renewLicense(reader.getId(), offlinePackage.getPackageId(), device.getId())
        );

        assertEquals("OFFLINE_PACKAGE_OUTDATED", exception.getErrorCode());
        assertTrue(offlinePackage.getRevoked());
        verify(packages).save(offlinePackage);
        verify(signer, never()).issue(any(), any(), any());
    }
}
