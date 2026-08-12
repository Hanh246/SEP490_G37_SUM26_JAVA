package com.sep.comiverse.service;

import com.sep.comiverse.dto.request.AuthRequest;
import com.sep.comiverse.dto.response.DeviceOtpChallengeResponse;
import com.sep.comiverse.dto.response.LoginDeviceResponse;
import com.sep.comiverse.entity.LoginDeviceChallengeEntity;
import com.sep.comiverse.entity.LoginDeviceEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.ILoginDeviceChallengeRepository;
import com.sep.comiverse.repository.ILoginDeviceRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.util.EmailUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class LoginDeviceService {

    private static final Pattern DEVICE_ID_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{16,128}");
    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final SecureRandom OTP_RANDOM = new SecureRandom();
    private static final String REPLACE_OPERATION = "REPLACE";
    private static final String REVOKE_OPERATION = "REVOKE";

    private final ILoginDeviceRepository deviceRepository;
    private final ILoginDeviceChallengeRepository challengeRepository;
    private final IUserRepository userRepository;
    private final OfflineDeviceService offlineDeviceService;
    private final EmailUtil emailUtil;
    private final PasswordEncoder passwordEncoder;
    private final int maxDevicesPerUser;

    public LoginDeviceService(
            ILoginDeviceRepository deviceRepository,
            ILoginDeviceChallengeRepository challengeRepository,
            IUserRepository userRepository,
            OfflineDeviceService offlineDeviceService,
            EmailUtil emailUtil,
            PasswordEncoder passwordEncoder,
            @Value("${login-device.max-devices-per-user:3}") int maxDevicesPerUser
    ) {
        this.deviceRepository = deviceRepository;
        this.challengeRepository = challengeRepository;
        this.userRepository = userRepository;
        this.offlineDeviceService = offlineDeviceService;
        this.emailUtil = emailUtil;
        this.passwordEncoder = passwordEncoder;
        this.maxDevicesPerUser = Math.max(1, maxDevicesPerUser);
    }

    @Transactional
    public LoginDecision beginLogin(UserEntity user, AuthRequest request) {
        if (request.getDeviceId() == null || request.getDeviceId().isBlank()) {
            return LoginDecision.allowed(user.getId(), null);
        }
        String deviceId = request.getDeviceId().trim();
        if (!DEVICE_ID_PATTERN.matcher(deviceId).matches()) {
            throw new CustomException(400, "Invalid device identifier", HttpStatus.BAD_REQUEST);
        }
        String platform = normalizePlatform(request.getPlatform());
        String name = normalizeDeviceName(request.getDeviceName(), platform);
        String deviceIdHash = sha256(deviceId);
        Instant now = Instant.now();

        userRepository.lockById(user.getId()).orElseThrow(() ->
                new CustomException(404, "User not found", HttpStatus.NOT_FOUND));
        LoginDeviceEntity existing = deviceRepository
                .findByUserIdAndDeviceIdHashAndDeletedFalse(user.getId(), deviceIdHash)
                .orElse(null);
        if (existing != null && !Boolean.TRUE.equals(existing.getRevoked())) {
            existing.setDeviceName(name);
            existing.setPlatform(platform);
            existing.setLastSeenAt(now);
            return LoginDecision.allowed(user.getId(), deviceRepository.save(existing).getId());
        }

        if (deviceRepository.countByUserIdAndRevokedFalseAndDeletedFalse(user.getId()) < maxDevicesPerUser) {
            LoginDeviceEntity enrolled = existing == null
                    ? LoginDeviceEntity.builder()
                    .user(user)
                    .deviceIdHash(deviceIdHash)
                    .deviceName(name)
                    .platform(platform)
                    .verifiedAt(now)
                    .lastSeenAt(now)
                    .revoked(false)
                    .build()
                    : existing;
            enrolled.setRevoked(false);
            enrolled.setDeviceName(name);
            enrolled.setPlatform(platform);
            enrolled.setVerifiedAt(now);
            enrolled.setLastSeenAt(now);
            LoginDeviceEntity saved = deviceRepository.save(enrolled);
            offlineDeviceService.restoreMatchingDevice(user.getId(), deviceIdHash);
            return LoginDecision.allowed(user.getId(), saved.getId());
        }

        LoginDeviceChallengeEntity challenge = createChallenge(
                user,
                REPLACE_OPERATION,
                deviceIdHash,
                name,
                platform,
                null
        );
        return LoginDecision.verificationRequired(
                challenge.getChallengeId(),
                challenge.getExpiresAt(),
                list(user.getId(), null)
        );
    }

    @Transactional(noRollbackFor = CustomException.class)
    public LoginDecision confirmReplacement(UUID challengeId, UUID deviceToRemoveId, String otp) {
        LoginDeviceChallengeEntity challenge = requireChallenge(challengeId, REPLACE_OPERATION);
        verifyOtp(challenge, otp);
        UserEntity user = userRepository.lockById(challenge.getUserId()).orElseThrow(() ->
                new CustomException(404, "User not found", HttpStatus.NOT_FOUND));
        LoginDeviceEntity removed = deviceRepository
                .findByIdAndUserIdAndDeletedFalse(deviceToRemoveId, user.getId())
                .filter(device -> !Boolean.TRUE.equals(device.getRevoked()))
                .orElseThrow(() -> new CustomException(404, "Login device not found", HttpStatus.NOT_FOUND));
        if (removed.getDeviceIdHash().equals(challenge.getDeviceIdHash())) {
            throw new CustomException(400, "Select a different device to remove", HttpStatus.BAD_REQUEST);
        }
        revokeDevice(removed);

        Instant now = Instant.now();
        LoginDeviceEntity replacement = deviceRepository
                .findByUserIdAndDeviceIdHashAndDeletedFalse(user.getId(), challenge.getDeviceIdHash())
                .orElseGet(() -> LoginDeviceEntity.builder()
                        .user(user)
                        .deviceIdHash(challenge.getDeviceIdHash())
                        .build());
        replacement.setDeviceName(challenge.getDeviceName());
        replacement.setPlatform(challenge.getPlatform());
        replacement.setVerifiedAt(now);
        replacement.setLastSeenAt(now);
        replacement.setRevoked(false);
        replacement.setDeleted(false);
        LoginDeviceEntity saved = deviceRepository.save(replacement);
        offlineDeviceService.restoreMatchingDevice(user.getId(), challenge.getDeviceIdHash());
        challenge.setConsumed(true);
        challengeRepository.save(challenge);
        return LoginDecision.allowed(user.getId(), saved.getId());
    }

    @Transactional(readOnly = true)
    public List<LoginDeviceResponse> list(UUID userId, UUID currentDeviceId) {
        return deviceRepository
                .findAllByUserIdAndRevokedFalseAndDeletedFalseOrderByLastSeenAtDesc(userId)
                .stream()
                .map(device -> toResponse(device, currentDeviceId))
                .toList();
    }

    @Transactional
    public DeviceOtpChallengeResponse requestRevocation(UUID userId, UUID targetDeviceId) {
        UserEntity user = userRepository.findByIdWithRole(userId).orElseThrow(() ->
                new CustomException(404, "User not found", HttpStatus.NOT_FOUND));
        LoginDeviceEntity target = deviceRepository
                .findByIdAndUserIdAndDeletedFalse(targetDeviceId, userId)
                .filter(device -> !Boolean.TRUE.equals(device.getRevoked()))
                .orElseThrow(() -> new CustomException(404, "Login device not found", HttpStatus.NOT_FOUND));
        LoginDeviceChallengeEntity challenge = createChallenge(
                user,
                REVOKE_OPERATION,
                null,
                null,
                null,
                target.getId()
        );
        return DeviceOtpChallengeResponse.builder()
                .challengeId(challenge.getChallengeId())
                .expiresAt(challenge.getExpiresAt())
                .build();
    }

    @Transactional(noRollbackFor = CustomException.class)
    public UUID confirmRevocation(UUID userId, UUID challengeId, String otp) {
        LoginDeviceChallengeEntity challenge = requireChallenge(challengeId, REVOKE_OPERATION);
        if (!userId.equals(challenge.getUserId())) {
            throw new CustomException(403, "Device challenge does not belong to this account", HttpStatus.FORBIDDEN);
        }
        verifyOtp(challenge, otp);
        LoginDeviceEntity target = deviceRepository
                .findByIdAndUserIdAndDeletedFalse(challenge.getTargetDeviceId(), userId)
                .filter(device -> !Boolean.TRUE.equals(device.getRevoked()))
                .orElseThrow(() -> new CustomException(404, "Login device not found", HttpStatus.NOT_FOUND));
        revokeDevice(target);
        challenge.setConsumed(true);
        challengeRepository.save(challenge);
        return target.getId();
    }

    @Transactional(readOnly = true)
    public boolean isActive(UUID userId, UUID deviceId) {
        return deviceRepository.isActive(deviceId, userId);
    }

    private LoginDeviceChallengeEntity createChallenge(
            UserEntity user,
            String operation,
            String deviceIdHash,
            String deviceName,
            String platform,
            UUID targetDeviceId
    ) {
        Instant now = Instant.now();
        if (challengeRepository.countByUserIdAndCreatedAtAfterAndDeletedFalse(
                user.getId(), now.minus(Duration.ofHours(1))) >= 5) {
            throw new CustomException(
                    429,
                    "Too many device verification requests. Please try again later.",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }
        String otp = String.format("%06d", OTP_RANDOM.nextInt(1_000_000));
        LoginDeviceChallengeEntity challenge = challengeRepository.save(
                LoginDeviceChallengeEntity.builder()
                        .challengeId(UUID.randomUUID())
                        .userId(user.getId())
                        .operation(operation)
                        .deviceIdHash(deviceIdHash)
                        .deviceName(deviceName)
                        .platform(platform)
                        .targetDeviceId(targetDeviceId)
                        .otpHash(passwordEncoder.encode(otp))
                        .expiresAt(now.plus(OTP_TTL))
                        .attemptCount(0)
                        .consumed(false)
                        .build()
        );
        emailUtil.sendDeviceVerificationOtp(user.getEmail(), otp, user.getFullName(), deviceName);
        return challenge;
    }

    private LoginDeviceChallengeEntity requireChallenge(UUID challengeId, String operation) {
        LoginDeviceChallengeEntity challenge = challengeRepository
                .findByChallengeIdAndConsumedFalseAndDeletedFalse(challengeId)
                .orElseThrow(() -> new CustomException(400, "Device verification challenge is invalid", HttpStatus.BAD_REQUEST));
        if (!operation.equals(challenge.getOperation()) || !challenge.getExpiresAt().isAfter(Instant.now())) {
            throw new CustomException(400, "Device verification challenge has expired", HttpStatus.BAD_REQUEST);
        }
        return challenge;
    }

    private void verifyOtp(LoginDeviceChallengeEntity challenge, String otp) {
        if (challenge.getAttemptCount() >= MAX_OTP_ATTEMPTS) {
            challenge.setConsumed(true);
            challengeRepository.save(challenge);
            throw new CustomException(429, "Too many incorrect OTP attempts", HttpStatus.TOO_MANY_REQUESTS);
        }
        if (!passwordEncoder.matches(otp == null ? "" : otp.trim(), challenge.getOtpHash())) {
            challenge.setAttemptCount(challenge.getAttemptCount() + 1);
            challengeRepository.save(challenge);
            throw new CustomException(400, "Invalid device verification OTP", HttpStatus.BAD_REQUEST);
        }
    }

    private void revokeDevice(LoginDeviceEntity device) {
        device.setRevoked(true);
        device.setLastSeenAt(Instant.now());
        deviceRepository.save(device);
        offlineDeviceService.revokeMatchingDevice(device.getUser().getId(), device.getDeviceIdHash());
    }

    private LoginDeviceResponse toResponse(LoginDeviceEntity device, UUID currentDeviceId) {
        return LoginDeviceResponse.builder()
                .id(device.getId())
                .deviceName(device.getDeviceName())
                .platform(device.getPlatform())
                .verifiedAt(device.getVerifiedAt())
                .lastSeenAt(device.getLastSeenAt())
                .current(device.getId().equals(currentDeviceId))
                .build();
    }

    private String normalizePlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            throw new CustomException(400, "Device platform is required", HttpStatus.BAD_REQUEST);
        }
        String normalized = platform.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("android") && !normalized.equals("ios")) {
            throw new CustomException(400, "Unsupported device platform", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeDeviceName(String value, String platform) {
        if (value == null || value.isBlank()) {
            return platform.equals("ios") ? "iPhone or iPad" : "Android device";
        }
        return value.trim().substring(0, Math.min(120, value.trim().length()));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record LoginDecision(
            boolean verificationRequired,
            UUID userId,
            UUID deviceId,
            UUID challengeId,
            Instant expiresAt,
            List<LoginDeviceResponse> devices
    ) {
        public static LoginDecision allowed(UUID userId, UUID deviceId) {
            return new LoginDecision(false, userId, deviceId, null, null, List.of());
        }

        public static LoginDecision verificationRequired(
                UUID challengeId,
                Instant expiresAt,
                List<LoginDeviceResponse> devices
        ) {
            return new LoginDecision(true, null, null, challengeId, expiresAt, devices);
        }
    }
}
