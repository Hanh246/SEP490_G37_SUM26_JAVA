package com.sep.comiverse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.config.OfflineDownloadProperties;
import com.sep.comiverse.entity.OfflineDeviceEntity;
import com.sep.comiverse.entity.OfflineLicenseEntity;
import com.sep.comiverse.entity.OfflinePackageEntity;
import com.sep.comiverse.exception.OfflineDownloadException;
import com.sep.comiverse.repository.IOfflineLicenseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class OfflineLicenseSigner {

    private static final Duration MAXIMUM_LICENSE_DURATION = Duration.ofDays(7);

    private final OfflineDownloadProperties properties;
    private final OfflineDownloadCryptoService cryptoService;
    private final IOfflineLicenseRepository licenseRepository;
    private final ObjectMapper objectMapper;

    public OfflineLicenseSigner(
            OfflineDownloadProperties properties,
            OfflineDownloadCryptoService cryptoService,
            IOfflineLicenseRepository licenseRepository,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.cryptoService = cryptoService;
        this.licenseRepository = licenseRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SignedOfflineLicense issue(
            OfflinePackageEntity offlinePackage,
            OfflineDeviceEntity device,
            Instant serverTime
    ) {
        cryptoService.requireAvailable();
        enforceRateLimit(offlinePackage.getUserId(), serverTime);

        Duration configuredDuration = properties.getLicenseDuration();
        if (configuredDuration == null || configuredDuration.isZero() || configuredDuration.isNegative()) {
            throw new OfflineDownloadException(
                    "OFFLINE_DOWNLOADS_UNAVAILABLE",
                    "Offline license duration is not configured safely",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
        Duration duration = configuredDuration.compareTo(MAXIMUM_LICENSE_DURATION) > 0
                ? MAXIMUM_LICENSE_DURATION
                : configuredDuration;
        Instant expiresAt = serverTime.plus(duration);
        UUID licenseId = UUID.randomUUID();

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "EdDSA");
        header.put("typ", "JWT");
        header.put("kid", properties.getSigningKeyId());

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", properties.getIssuer());
        claims.put("aud", properties.getAudience());
        claims.put("jti", licenseId.toString());
        claims.put("sub", offlinePackage.getUserId().toString());
        claims.put("userId", offlinePackage.getUserId().toString());
        claims.put("chapterId", offlinePackage.getChapterId().toString());
        claims.put("comicId", offlinePackage.getComicId().toString());
        claims.put("packageId", offlinePackage.getPackageId().toString());
        claims.put("deviceKeyId", device.getId().toString());
        claims.put("deviceIdHash", device.getDeviceIdHash());
        claims.put("deviceKeySha256", device.getPublicKeySha256());
        claims.put("contentRevision", offlinePackage.getContentRevision());
        claims.put("manifestSha256", offlinePackage.getManifestSha256());
        claims.put("packageSha256", offlinePackage.getPackageSha256());
        claims.put("packageSize", offlinePackage.getPackageSize());
        claims.put("wrappedKeySha256", cryptoService.sha256Hex(
                cryptoService.decodeFlexible(offlinePackage.getWrappedContentKey())
        ));
        claims.put("keyAlgorithm", offlinePackage.getKeyAlgorithm());
        claims.put("formatVersion", offlinePackage.getFormatVersion());
        claims.put("iat", serverTime.getEpochSecond());
        claims.put("nbf", serverTime.minusSeconds(30).getEpochSecond());
        claims.put("exp", expiresAt.getEpochSecond());
        claims.put("serverTime", serverTime.toString());
        claims.put("offlineUntil", expiresAt.toString());

        try {
            String encodedHeader = cryptoService.encodeUrl(objectMapper.writeValueAsBytes(header));
            String encodedPayload = cryptoService.encodeUrl(objectMapper.writeValueAsBytes(claims));
            String signingInput = encodedHeader + "." + encodedPayload;
            String token = signingInput + "." + cryptoService.encodeUrl(
                    cryptoService.signLicense(signingInput.getBytes(StandardCharsets.US_ASCII))
            );

            OfflineLicenseEntity record = OfflineLicenseEntity.builder()
                    .licenseId(licenseId)
                    .packageId(offlinePackage.getPackageId())
                    .userId(offlinePackage.getUserId())
                    .chapterId(offlinePackage.getChapterId())
                    .offlineDeviceId(device.getId())
                    .issuedAt(serverTime)
                    .expiresAt(expiresAt)
                    .revoked(false)
                    .build();
            licenseRepository.save(record);
            return new SignedOfflineLicense(token, licenseId, serverTime, expiresAt);
        } catch (OfflineDownloadException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new OfflineDownloadException(
                    "OFFLINE_LICENSE_FAILED",
                    "The offline license could not be issued",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private void enforceRateLimit(UUID userId, Instant now) {
        long issued = licenseRepository.countByUserIdAndCreatedAtAfterAndDeletedFalse(
                userId,
                now.minus(Duration.ofHours(1))
        );
        if (issued >= properties.getMaxLicensesPerHour()) {
            throw new OfflineDownloadException(
                    "LICENSE_RATE_LIMITED",
                    "Too many offline licenses were requested. Please try again later",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }
    }

    public record SignedOfflineLicense(
            String token,
            UUID licenseId,
            Instant issuedAt,
            Instant expiresAt
    ) {
    }
}
