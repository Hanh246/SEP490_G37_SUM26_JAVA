package com.sep.comiverse.unit.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.config.OfflineDownloadProperties;
import com.sep.comiverse.entity.OfflineDeviceEntity;
import com.sep.comiverse.entity.OfflinePackageEntity;
import com.sep.comiverse.repository.IOfflineLicenseRepository;
import com.sep.comiverse.service.OfflineDownloadCryptoService;
import com.sep.comiverse.service.OfflineLicenseSigner;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OfflineLicenseSignerTest {

    @Test
    void signsRequiredBindingsAndNeverExceedsSevenDays() throws Exception {
        OfflineDownloadProperties properties = OfflineDownloadCryptoServiceTest.enabledProperties();
        properties.setLicenseDuration(Duration.ofDays(7));
        OfflineDownloadCryptoService crypto = new OfflineDownloadCryptoService(properties);
        ReflectionTestUtils.invokeMethod(crypto, "initialize");
        IOfflineLicenseRepository repository = mock(IOfflineLicenseRepository.class);
        when(repository.countByUserIdAndCreatedAtAfterAndDeletedFalse(any(), any())).thenReturn(0L);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ObjectMapper objectMapper = new ObjectMapper();
        OfflineLicenseSigner signer = new OfflineLicenseSigner(properties, crypto, repository, objectMapper);

        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        OfflineDeviceEntity device = OfflineDeviceEntity.builder()
                .userId(userId)
                .deviceIdHash("a".repeat(64))
                .publicKeySha256("b".repeat(64))
                .publicKeyBase64("unused")
                .lastSeenAt(Instant.now())
                .build();
        device.setId(deviceId);
        OfflinePackageEntity offlinePackage = OfflinePackageEntity.builder()
                .packageId(packageId)
                .userId(userId)
                .chapterId(UUID.randomUUID())
                .comicId(UUID.randomUUID())
                .offlineDeviceId(deviceId)
                .deviceKeySha256(device.getPublicKeySha256())
                .contentRevision("c".repeat(64))
                .sourceDescriptorSha256("d".repeat(64))
                .manifestSha256("e".repeat(64))
                .packageSha256("f".repeat(64))
                .packageSize(2048L)
                .wrappedContentKey(crypto.encodeUrl(crypto.randomBytes(256)))
                .keyAlgorithm(OfflineDownloadCryptoService.KEY_WRAP_ALGORITHM)
                .formatVersion(1)
                .build();
        Instant now = Instant.parse("2026-08-04T12:00:00Z");

        OfflineLicenseSigner.SignedOfflineLicense result = signer.issue(offlinePackage, device, now);
        String[] parts = result.token().split("\\.");
        Map<String, Object> claims = objectMapper.readValue(
                Base64.getUrlDecoder().decode(parts[1]),
                new TypeReference<>() { }
        );
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(properties.getSigningPublicKey()))
        ));
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));

        assertTrue(verifier.verify(Base64.getUrlDecoder().decode(parts[2])));
        assertEquals(packageId.toString(), claims.get("packageId"));
        assertEquals(deviceId.toString(), claims.get("deviceKeyId"));
        assertEquals(device.getDeviceIdHash(), claims.get("deviceIdHash"));
        assertEquals(OfflineDownloadCryptoService.KEY_WRAP_ALGORITHM, claims.get("keyAlgorithm"));
        assertEquals(604800L, ((Number) claims.get("exp")).longValue() - ((Number) claims.get("iat")).longValue());
    }
}
