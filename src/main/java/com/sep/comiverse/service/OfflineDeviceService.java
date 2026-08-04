package com.sep.comiverse.service;

import com.sep.comiverse.config.OfflineDownloadProperties;
import com.sep.comiverse.dto.request.CreateOfflineDeviceChallengeRequest;
import com.sep.comiverse.dto.request.EnrollOfflineDeviceRequest;
import com.sep.comiverse.dto.response.OfflineDeviceChallengeResponse;
import com.sep.comiverse.dto.response.OfflineDeviceResponse;
import com.sep.comiverse.entity.OfflineDeviceChallengeEntity;
import com.sep.comiverse.entity.OfflineDeviceEntity;
import com.sep.comiverse.exception.OfflineDownloadException;
import com.sep.comiverse.repository.IOfflineDeviceChallengeRepository;
import com.sep.comiverse.repository.IOfflineDeviceRepository;
import com.sep.comiverse.repository.IOfflineLicenseRepository;
import com.sep.comiverse.repository.IOfflinePackageRepository;
import com.sep.comiverse.repository.IUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class OfflineDeviceService {

    private static final Pattern DEVICE_ID_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{16,128}");

    private final OfflineDownloadProperties properties;
    private final OfflineDownloadCryptoService cryptoService;
    private final IOfflineDeviceRepository deviceRepository;
    private final IOfflineDeviceChallengeRepository challengeRepository;
    private final IOfflinePackageRepository packageRepository;
    private final IOfflineLicenseRepository licenseRepository;
    private final IUserRepository userRepository;

    public OfflineDeviceService(
            OfflineDownloadProperties properties,
            OfflineDownloadCryptoService cryptoService,
            IOfflineDeviceRepository deviceRepository,
            IOfflineDeviceChallengeRepository challengeRepository,
            IOfflinePackageRepository packageRepository,
            IOfflineLicenseRepository licenseRepository,
            IUserRepository userRepository
    ) {
        this.properties = properties;
        this.cryptoService = cryptoService;
        this.deviceRepository = deviceRepository;
        this.challengeRepository = challengeRepository;
        this.packageRepository = packageRepository;
        this.licenseRepository = licenseRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public OfflineDeviceChallengeResponse createChallenge(
            UUID userId,
            CreateOfflineDeviceChallengeRequest request
    ) {
        cryptoService.requireAvailable();
        Instant now = Instant.now();
        if (challengeRepository.countByUserIdAndCreatedAtAfterAndDeletedFalse(
                userId,
                now.minus(Duration.ofHours(1))
        ) >= properties.getMaxChallengesPerHour()) {
            throw new OfflineDownloadException(
                    "DEVICE_CHALLENGE_RATE_LIMITED",
                    "Too many device enrollment attempts were made. Please try again later",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }

        String rawDeviceId = request.getDeviceId().trim();
        if (!DEVICE_ID_PATTERN.matcher(rawDeviceId).matches()) {
            throw new OfflineDownloadException(
                    "INVALID_DEVICE_ID",
                    "The device identifier format is invalid",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }

        PublicKey publicKey = cryptoService.parseDevicePublicKey(request.getDevicePublicKey());
        String publicKeyBase64 = cryptoService.canonicalPublicKeyBase64(publicKey);
        String publicKeySha256 = cryptoService.sha256Hex(publicKey.getEncoded());
        String deviceIdHash = cryptoService.sha256Hex(rawDeviceId.getBytes(StandardCharsets.UTF_8));

        deviceRepository.findByUserIdAndDeviceIdHashAndDeletedFalse(userId, deviceIdHash)
                .ifPresent(existing -> validateExistingBinding(existing, publicKeySha256));

        UUID challengeId = UUID.randomUUID();
        String nonce = cryptoService.encodeUrl(cryptoService.randomBytes(32));
        String challengeText = String.join("\n",
                "COMIVERSE-OFFLINE-DEVICE-V1",
                challengeId.toString(),
                userId.toString(),
                deviceIdHash,
                publicKeySha256,
                nonce
        );
        String encodedChallenge = cryptoService.encodeUrl(challengeText.getBytes(StandardCharsets.UTF_8));
        Instant expiresAt = now.plus(properties.getChallengeTtl());

        challengeRepository.save(OfflineDeviceChallengeEntity.builder()
                .challengeId(challengeId)
                .userId(userId)
                .deviceIdHash(deviceIdHash)
                .deviceName(normalizeName(request.getDeviceName()))
                .publicKeySha256(publicKeySha256)
                .publicKeyBase64(publicKeyBase64)
                .challengeBase64(encodedChallenge)
                .expiresAt(expiresAt)
                .consumed(false)
                .build());

        return OfflineDeviceChallengeResponse.builder()
                .challengeId(challengeId.toString())
                .challenge(encodedChallenge)
                .signatureAlgorithm(OfflineDownloadCryptoService.DEVICE_PROOF_ALGORITHM)
                .expiresAt(expiresAt)
                .serverTime(now)
                .build();
    }

    @Transactional
    public OfflineDeviceResponse enroll(UUID userId, EnrollOfflineDeviceRequest request) {
        cryptoService.requireAvailable();
        UUID challengeId;
        try {
            challengeId = UUID.fromString(request.getChallengeId().trim());
        } catch (Exception exception) {
            throw invalidChallenge();
        }

        OfflineDeviceChallengeEntity challenge = challengeRepository
                .findByChallengeIdAndUserIdAndConsumedFalseAndDeletedFalse(challengeId, userId)
                .orElseThrow(this::invalidChallenge);
        Instant now = Instant.now();
        if (!challenge.getExpiresAt().isAfter(now)) {
            throw new OfflineDownloadException(
                    "DEVICE_CHALLENGE_EXPIRED",
                    "The device enrollment challenge has expired",
                    HttpStatus.GONE
            );
        }

        PublicKey publicKey = cryptoService.parseDevicePublicKey(challenge.getPublicKeyBase64());
        byte[] challengeBytes = cryptoService.decodeFlexible(challenge.getChallengeBase64());
        if (!cryptoService.verifyDeviceProof(publicKey, challengeBytes, request.getSignature())) {
            throw new OfflineDownloadException(
                    "DEVICE_PROOF_INVALID",
                    "The device did not prove possession of its private key",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }

        OfflineDeviceEntity device = deviceRepository
                .findByUserIdAndDeviceIdHashAndDeletedFalse(userId, challenge.getDeviceIdHash())
                .orElse(null);
        if (device == null) {
            userRepository.lockById(userId).orElseThrow(() -> new OfflineDownloadException(
                    "USER_NOT_FOUND",
                    "The signed-in account no longer exists",
                    HttpStatus.NOT_FOUND
            ));
            device = deviceRepository
                    .findByUserIdAndDeviceIdHashAndDeletedFalse(userId, challenge.getDeviceIdHash())
                    .orElse(null);
            if (device == null) {
                if (deviceRepository.countByUserIdAndRevokedFalseAndDeletedFalse(userId)
                        >= properties.getMaxDevicesPerUser()) {
                    throw new OfflineDownloadException(
                            "OFFLINE_DEVICE_LIMIT_REACHED",
                            "The maximum number of offline devices has been reached",
                            HttpStatus.CONFLICT
                    );
                }
                device = OfflineDeviceEntity.builder()
                        .userId(userId)
                        .deviceIdHash(challenge.getDeviceIdHash())
                        .deviceName(challenge.getDeviceName())
                        .publicKeySha256(challenge.getPublicKeySha256())
                        .publicKeyBase64(challenge.getPublicKeyBase64())
                        .revoked(false)
                        .lastSeenAt(now)
                        .build();
            } else {
                validateExistingBinding(device, challenge.getPublicKeySha256());
                device.setDeviceName(challenge.getDeviceName());
                device.setLastSeenAt(now);
            }
        } else {
            validateExistingBinding(device, challenge.getPublicKeySha256());
            device.setDeviceName(challenge.getDeviceName());
            device.setLastSeenAt(now);
        }

        OfflineDeviceEntity saved = deviceRepository.save(device);
        challenge.setConsumed(true);
        challengeRepository.save(challenge);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public OfflineDeviceEntity requireActiveDevice(UUID userId, UUID deviceKeyId) {
        OfflineDeviceEntity device = deviceRepository.findByIdAndUserIdAndDeletedFalse(deviceKeyId, userId)
                .orElseThrow(() -> new OfflineDownloadException(
                        "OFFLINE_DEVICE_NOT_FOUND",
                        "The offline device is not registered for this account",
                        HttpStatus.NOT_FOUND
                ));
        if (Boolean.TRUE.equals(device.getRevoked())) {
            throw new OfflineDownloadException(
                    "OFFLINE_DEVICE_REVOKED",
                    "The offline device has been revoked",
                    HttpStatus.FORBIDDEN
            );
        }
        return device;
    }

    @Transactional(readOnly = true)
    public List<OfflineDeviceResponse> list(UUID userId) {
        return deviceRepository.findAllByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void revoke(UUID userId, UUID deviceKeyId) {
        OfflineDeviceEntity device = deviceRepository.findByIdAndUserIdAndDeletedFalse(deviceKeyId, userId)
                .orElseThrow(() -> new OfflineDownloadException(
                        "OFFLINE_DEVICE_NOT_FOUND",
                        "The offline device is not registered for this account",
                        HttpStatus.NOT_FOUND
                ));
        device.setRevoked(true);
        device.setLastSeenAt(Instant.now());
        deviceRepository.save(device);
        packageRepository.revokeByDevice(device.getId(), userId);
        licenseRepository.revokeByDevice(device.getId(), userId);
    }

    private void validateExistingBinding(OfflineDeviceEntity existing, String fingerprint) {
        if (Boolean.TRUE.equals(existing.getRevoked())) {
            throw new OfflineDownloadException(
                    "OFFLINE_DEVICE_REVOKED",
                    "This device identifier was revoked and cannot be enrolled again",
                    HttpStatus.FORBIDDEN
            );
        }
        if (!existing.getPublicKeySha256().equalsIgnoreCase(fingerprint)) {
            throw new OfflineDownloadException(
                    "OFFLINE_DEVICE_KEY_MISMATCH",
                    "This device identifier is already bound to a different key",
                    HttpStatus.CONFLICT
            );
        }
    }

    private OfflineDeviceResponse toResponse(OfflineDeviceEntity device) {
        return OfflineDeviceResponse.builder()
                .deviceKeyId(device.getId())
                .deviceName(device.getDeviceName())
                .publicKeySha256(device.getPublicKeySha256())
                .enrolledAt(device.getCreatedAt())
                .lastSeenAt(device.getLastSeenAt())
                .serverTime(Instant.now())
                .revoked(Boolean.TRUE.equals(device.getRevoked()))
                .build();
    }

    private String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private OfflineDownloadException invalidChallenge() {
        return new OfflineDownloadException(
                "DEVICE_CHALLENGE_INVALID",
                "The device enrollment challenge is invalid or was already used",
                HttpStatus.UNPROCESSABLE_ENTITY
        );
    }
}
