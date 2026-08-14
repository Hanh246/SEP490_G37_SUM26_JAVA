package com.sep.comiverse.unit.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.config.OfflineDownloadProperties;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.ComicEntity;
import com.sep.comiverse.entity.OfflineDeviceEntity;
import com.sep.comiverse.repository.IOfflinePackageRepository;
import com.sep.comiverse.service.OfflineChapterPackageService;
import com.sep.comiverse.service.OfflineDownloadCryptoService;
import com.sep.comiverse.service.OfflineSourceImageService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.MGF1ParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OfflineChapterPackageServiceTest {

    @Test
    void createsRandomAccessPageFrameWithAuthenticatedImmutableAad() throws Exception {
        OfflineDownloadProperties properties = OfflineDownloadCryptoServiceTest.enabledProperties();
        OfflineDownloadCryptoService crypto = new OfflineDownloadCryptoService(properties);
        ReflectionTestUtils.invokeMethod(crypto, "initialize");
        OfflineSourceImageService source = mock(OfflineSourceImageService.class);
        IOfflinePackageRepository repository = mock(IOfflinePackageRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        byte[] pageBytes = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3, 4};
        when(source.download("https://res.cloudinary.com/demo/page.png"))
                .thenAnswer(invocation -> new OfflineSourceImageService.DownloadedPage(pageBytes.clone(), "image/png"));
        when(repository.countByUserIdAndCreatedAtAfterAndDeletedFalse(any(), any())).thenReturn(0L);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OfflineChapterPackageService service = new OfflineChapterPackageService(
                properties,
                crypto,
                source,
                repository,
                objectMapper
        );
        UUID userId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID comicId = UUID.randomUUID();
        UUID deviceKeyId = UUID.randomUUID();
        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(2048);
        KeyPair deviceKeys = rsa.generateKeyPair();
        OfflineDeviceEntity device = OfflineDeviceEntity.builder()
                .userId(userId)
                .deviceIdHash("1".repeat(64))
                .publicKeySha256(crypto.sha256Hex(deviceKeys.getPublic().getEncoded()))
                .publicKeyBase64(Base64.getEncoder().encodeToString(deviceKeys.getPublic().getEncoded()))
                .lastSeenAt(Instant.now())
                .build();
        device.setId(deviceKeyId);
        ComicEntity comic = ComicEntity.builder().title("Test").build();
        comic.setId(comicId);
        ChapterEntity chapter = ChapterEntity.builder()
                .comic(comic)
                .images(List.of("https://res.cloudinary.com/demo/page.png"))
                .build();
        chapter.setId(chapterId);

        OfflineChapterPackageService.PreparedPackage prepared = service.create(chapter, device, Instant.now());
        try (var artifact = prepared.artifact()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            artifact.copyTo(bytes);
            byte[] packageBytes = bytes.toByteArray();
            assertEquals(-1, indexOf(packageBytes, pageBytes));
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(packageBytes));
            assertArrayEquals(OfflineChapterPackageService.MAGIC, input.readNBytes(5));
            int manifestLength = input.readInt();
            byte[] manifestBytes = input.readNBytes(manifestLength);
            Map<String, Object> manifest = objectMapper.readValue(manifestBytes, new TypeReference<>() { });
            List<Map<String, Object>> pages = (List<Map<String, Object>>) manifest.get("pages");
            Map<String, Object> page = pages.getFirst();
            byte[] ciphertext = input.readAllBytes();

            Cipher unwrap = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            unwrap.init(Cipher.DECRYPT_MODE, deviceKeys.getPrivate(), new OAEPParameterSpec(
                    "SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT
            ));
            byte[] contentKey = unwrap.doFinal(crypto.decodeFlexible(prepared.record().getWrappedContentKey()));
            String aad = String.join("|",
                    "CVPK1",
                    prepared.record().getPackageId().toString(),
                    userId.toString(),
                    chapterId.toString(),
                    device.getPublicKeySha256(),
                    prepared.record().getContentRevision(),
                    "1",
                    page.get("pageSha256").toString()
            );
            Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding");
            decrypt.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(contentKey, "AES"),
                    new GCMParameterSpec(128, Base64.getUrlDecoder().decode(page.get("nonce").toString()))
            );
            decrypt.updateAAD(aad.getBytes(StandardCharsets.UTF_8));

            assertArrayEquals(pageBytes, decrypt.doFinal(ciphertext));
            assertEquals("PAYLOAD", manifest.get("offsetBase"));
            assertEquals(1, ((Number) manifest.get("version")).intValue());
            assertEquals(0L, ((Number) page.get("offset")).longValue());
            assertEquals(ciphertext.length, ((Number) page.get("length")).intValue());
        }
    }

    private int indexOf(byte[] source, byte[] candidate) {
        outer:
        for (int start = 0; start <= source.length - candidate.length; start++) {
            for (int index = 0; index < candidate.length; index++) {
                if (source[start + index] != candidate[index]) {
                    continue outer;
                }
            }
            return start;
        }
        return -1;
    }
}
