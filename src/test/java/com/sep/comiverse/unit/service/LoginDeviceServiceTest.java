package com.sep.comiverse.unit.service;

import com.sep.comiverse.dto.request.AuthRequest;
import com.sep.comiverse.entity.LoginDeviceChallengeEntity;
import com.sep.comiverse.entity.LoginDeviceEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.ILoginDeviceChallengeRepository;
import com.sep.comiverse.repository.ILoginDeviceRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.service.LoginDeviceService;
import com.sep.comiverse.service.OfflineDeviceService;
import com.sep.comiverse.util.EmailUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginDeviceServiceTest {

    @Mock
    private ILoginDeviceRepository deviceRepository;
    @Mock
    private ILoginDeviceChallengeRepository challengeRepository;
    @Mock
    private IUserRepository userRepository;
    @Mock
    private OfflineDeviceService offlineDeviceService;
    @Mock
    private EmailUtil emailUtil;

    private LoginDeviceService service;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        service = new LoginDeviceService(
                deviceRepository,
                challengeRepository,
                userRepository,
                offlineDeviceService,
                emailUtil,
                passwordEncoder,
                3
        );
    }

    @Test
    void fourthDeviceRequiresEmailOtpAndReturnsReplaceableDevices() {
        UserEntity user = user();
        AuthRequest request = mobileRequest("device-installation-0004");
        List<LoginDeviceEntity> existingDevices = List.of(
                device(user, "hash-1", "Phone 1"),
                device(user, "hash-2", "Phone 2"),
                device(user, "hash-3", "Phone 3")
        );
        when(userRepository.lockById(user.getId())).thenReturn(Optional.of(user));
        when(deviceRepository.findByUserIdAndDeviceIdHashAndDeletedFalse(eq(user.getId()), any()))
                .thenReturn(Optional.empty());
        when(deviceRepository.countByUserIdAndRevokedFalseAndDeletedFalse(user.getId())).thenReturn(3L);
        when(deviceRepository.findAllByUserIdAndRevokedFalseAndDeletedFalseOrderByLastSeenAtDesc(user.getId()))
                .thenReturn(existingDevices);
        when(challengeRepository.countByUserIdAndCreatedAtAfterAndDeletedFalse(eq(user.getId()), any()))
                .thenReturn(0L);
        when(challengeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var decision = service.beginLogin(user, request);

        assertTrue(decision.verificationRequired());
        assertEquals(3, decision.devices().size());
        verify(emailUtil).sendDeviceVerificationOtp(
                eq(user.getEmail()), any(), eq(user.getFullName()), eq("New phone")
        );
    }

    @Test
    void correctOtpReplacesLoginDeviceAndRevokesItsOfflineAccess() {
        UserEntity user = user();
        LoginDeviceEntity removed = device(user, "old-device-hash", "Old phone");
        UUID challengeId = UUID.randomUUID();
        LoginDeviceChallengeEntity challenge = challenge(user, challengeId, "123456");
        when(challengeRepository.findByChallengeIdAndConsumedFalseAndDeletedFalse(challengeId))
                .thenReturn(Optional.of(challenge));
        when(userRepository.lockById(user.getId())).thenReturn(Optional.of(user));
        when(deviceRepository.findByIdAndUserIdAndDeletedFalse(removed.getId(), user.getId()))
                .thenReturn(Optional.of(removed));
        when(deviceRepository.findByUserIdAndDeviceIdHashAndDeletedFalse(user.getId(), "new-device-hash"))
                .thenReturn(Optional.empty());
        when(deviceRepository.save(any())).thenAnswer(invocation -> {
            LoginDeviceEntity saved = invocation.getArgument(0);
            if (saved.getId() == null) saved.setId(UUID.randomUUID());
            return saved;
        });

        var decision = service.confirmReplacement(challengeId, removed.getId(), "123456");

        assertFalse(decision.verificationRequired());
        assertEquals(user.getId(), decision.userId());
        assertTrue(removed.getRevoked());
        assertTrue(challenge.getConsumed());
        verify(offlineDeviceService).revokeMatchingDevice(user.getId(), "old-device-hash");
        verify(offlineDeviceService).restoreMatchingDevice(user.getId(), "new-device-hash");
    }

    @Test
    void incorrectOtpCannotReplaceOrRevokeADevice() {
        UserEntity user = user();
        LoginDeviceChallengeEntity challenge = challenge(user, UUID.randomUUID(), "123456");
        when(challengeRepository.findByChallengeIdAndConsumedFalseAndDeletedFalse(challenge.getChallengeId()))
                .thenReturn(Optional.of(challenge));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.confirmReplacement(challenge.getChallengeId(), UUID.randomUUID(), "000000")
        );

        assertEquals(1, challenge.getAttemptCount());
        assertEquals(400, error.getCode());
        verify(deviceRepository, never()).save(any());
        verify(offlineDeviceService, never()).revokeMatchingDevice(any(), any());
    }

    private UserEntity user() {
        UserEntity user = UserEntity.builder()
                .email("reader@example.com")
                .fullName("Reader One")
                .status("ACTIVE")
                .build();
        user.setId(UUID.randomUUID());
        user.setDeleted(false);
        return user;
    }

    private LoginDeviceEntity device(UserEntity user, String hash, String name) {
        LoginDeviceEntity device = LoginDeviceEntity.builder()
                .user(user)
                .deviceIdHash(hash)
                .deviceName(name)
                .platform("android")
                .revoked(false)
                .verifiedAt(Instant.now())
                .lastSeenAt(Instant.now())
                .build();
        device.setId(UUID.randomUUID());
        device.setDeleted(false);
        return device;
    }

    private AuthRequest mobileRequest(String deviceId) {
        AuthRequest request = new AuthRequest();
        request.setUsername("reader");
        request.setPassword("password");
        request.setDeviceId(deviceId);
        request.setDeviceName("New phone");
        request.setPlatform("android");
        return request;
    }

    private LoginDeviceChallengeEntity challenge(UserEntity user, UUID challengeId, String otp) {
        LoginDeviceChallengeEntity challenge = LoginDeviceChallengeEntity.builder()
                .challengeId(challengeId)
                .userId(user.getId())
                .operation("REPLACE")
                .deviceIdHash("new-device-hash")
                .deviceName("New phone")
                .platform("android")
                .otpHash(passwordEncoder.encode(otp))
                .expiresAt(Instant.now().plusSeconds(300))
                .attemptCount(0)
                .consumed(false)
                .build();
        challenge.setDeleted(false);
        return challenge;
    }

}
