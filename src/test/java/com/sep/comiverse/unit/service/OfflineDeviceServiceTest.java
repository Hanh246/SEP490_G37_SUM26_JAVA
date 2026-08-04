package com.sep.comiverse.unit.service;

import com.sep.comiverse.config.OfflineDownloadProperties;
import com.sep.comiverse.dto.request.CreateOfflineDeviceChallengeRequest;
import com.sep.comiverse.dto.request.EnrollOfflineDeviceRequest;
import com.sep.comiverse.entity.OfflineDeviceChallengeEntity;
import com.sep.comiverse.entity.OfflineDeviceEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.exception.OfflineDownloadException;
import com.sep.comiverse.repository.IOfflineDeviceChallengeRepository;
import com.sep.comiverse.repository.IOfflineDeviceRepository;
import com.sep.comiverse.repository.IOfflineLicenseRepository;
import com.sep.comiverse.repository.IOfflinePackageRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.service.OfflineDeviceService;
import com.sep.comiverse.service.OfflineDownloadCryptoService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OfflineDeviceServiceTest {

    @Test
    void consumesProofChallengeOnceAndCreatesBoundDevice() throws Exception {
        OfflineDownloadProperties properties = OfflineDownloadCryptoServiceTest.enabledProperties();
        OfflineDownloadCryptoService crypto = new OfflineDownloadCryptoService(properties);
        ReflectionTestUtils.invokeMethod(crypto, "initialize");
        IOfflineDeviceRepository devices = mock(IOfflineDeviceRepository.class);
        IOfflineDeviceChallengeRepository challenges = mock(IOfflineDeviceChallengeRepository.class);
        IOfflinePackageRepository packages = mock(IOfflinePackageRepository.class);
        IOfflineLicenseRepository licenses = mock(IOfflineLicenseRepository.class);
        IUserRepository users = mock(IUserRepository.class);
        OfflineDeviceService service = new OfflineDeviceService(
                properties,
                crypto,
                devices,
                challenges,
                packages,
                licenses,
                users
        );
        UUID userId = UUID.randomUUID();
        AtomicReference<OfflineDeviceChallengeEntity> storedChallenge = new AtomicReference<>();
        when(challenges.countByUserIdAndCreatedAtAfterAndDeletedFalse(any(), any())).thenReturn(0L);
        when(challenges.save(any())).thenAnswer(invocation -> {
            OfflineDeviceChallengeEntity challenge = invocation.getArgument(0);
            if (challenge.getId() == null) challenge.setId(UUID.randomUUID());
            if (challenge.getCreatedAt() == null) challenge.setCreatedAt(Instant.now());
            storedChallenge.set(challenge);
            return challenge;
        });
        when(challenges.findByChallengeIdAndUserIdAndConsumedFalseAndDeletedFalse(any(), any()))
                .thenAnswer(invocation -> {
                    OfflineDeviceChallengeEntity challenge = storedChallenge.get();
                    return challenge != null && !Boolean.TRUE.equals(challenge.getConsumed())
                            ? Optional.of(challenge)
                            : Optional.empty();
                });
        when(devices.findByUserIdAndDeviceIdHashAndDeletedFalse(any(), any())).thenReturn(Optional.empty());
        when(devices.countByUserIdAndRevokedFalseAndDeletedFalse(userId)).thenReturn(0L);
        when(devices.save(any())).thenAnswer(invocation -> {
            OfflineDeviceEntity device = invocation.getArgument(0);
            device.setId(UUID.randomUUID());
            device.setCreatedAt(Instant.now());
            return device;
        });
        when(users.lockById(userId)).thenReturn(Optional.of(new UserEntity()));

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair deviceKeys = keyPairGenerator.generateKeyPair();
        CreateOfflineDeviceChallengeRequest createRequest = new CreateOfflineDeviceChallengeRequest();
        createRequest.setDeviceId("installation-1234567890");
        createRequest.setDeviceName("Pixel Emulator");
        createRequest.setDevicePublicKey(Base64.getEncoder().encodeToString(deviceKeys.getPublic().getEncoded()));
        var challengeResponse = service.createChallenge(userId, createRequest);

        Signature signature = Signature.getInstance("RSASSA-PSS");
        signature.setParameter(new PSSParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1
        ));
        signature.initSign(deviceKeys.getPrivate());
        signature.update(crypto.decodeFlexible(challengeResponse.getChallenge()));
        EnrollOfflineDeviceRequest enrollRequest = new EnrollOfflineDeviceRequest();
        enrollRequest.setChallengeId(challengeResponse.getChallengeId());
        enrollRequest.setSignature(crypto.encodeUrl(signature.sign()));

        var enrolled = service.enroll(userId, enrollRequest);

        assertNotNull(enrolled.getDeviceKeyId());
        assertEquals(crypto.sha256Hex(deviceKeys.getPublic().getEncoded()), enrolled.getPublicKeySha256());
        OfflineDownloadException replay = assertThrows(
                OfflineDownloadException.class,
                () -> service.enroll(userId, enrollRequest)
        );
        assertEquals("DEVICE_CHALLENGE_INVALID", replay.getErrorCode());
    }
}
