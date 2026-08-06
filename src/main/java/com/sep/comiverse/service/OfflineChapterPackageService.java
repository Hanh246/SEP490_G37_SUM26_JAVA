package com.sep.comiverse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.config.OfflineDownloadProperties;
import com.sep.comiverse.entity.ChapterEntity;
import com.sep.comiverse.entity.OfflineDeviceEntity;
import com.sep.comiverse.entity.OfflinePackageEntity;
import com.sep.comiverse.exception.OfflineDownloadException;
import com.sep.comiverse.repository.IOfflinePackageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OfflineChapterPackageService {

    public static final int FORMAT_VERSION = 1;
    public static final byte[] MAGIC = "CVPK1".getBytes(StandardCharsets.US_ASCII);

    private final OfflineDownloadProperties properties;
    private final OfflineDownloadCryptoService cryptoService;
    private final OfflineSourceImageService sourceImageService;
    private final IOfflinePackageRepository packageRepository;
    private final ObjectMapper objectMapper;

    public OfflineChapterPackageService(
            OfflineDownloadProperties properties,
            OfflineDownloadCryptoService cryptoService,
            OfflineSourceImageService sourceImageService,
            IOfflinePackageRepository packageRepository,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.cryptoService = cryptoService;
        this.sourceImageService = sourceImageService;
        this.packageRepository = packageRepository;
        this.objectMapper = objectMapper;
    }

    public PreparedPackage create(ChapterEntity chapter, OfflineDeviceEntity device, Instant serverTime) {
        cryptoService.requireAvailable();
        validateLimits(chapter, serverTime, device.getUserId());

        List<PageFingerprint> fingerprints = fingerprintPages(chapter.getImages());
        String contentRevision = contentRevision(chapter, fingerprints);
        String sourceDescriptor = sourceDescriptor(chapter);
        UUID packageId = UUID.randomUUID();
        byte[] contentKey = cryptoService.generateContentKey();
        Path encryptedPayload = null;
        Path finalPackage = null;

        try {
            encryptedPayload = Files.createTempFile("comiverse-cvpack-payload-", ".bin");
            List<PageManifestEntry> entries = encryptPages(
                    chapter,
                    device,
                    packageId,
                    contentRevision,
                    fingerprints,
                    contentKey,
                    encryptedPayload
            );

            Map<String, Object> manifest = buildManifest(
                    chapter,
                    device,
                    packageId,
                    contentRevision,
                    entries
            );
            byte[] manifestBytes = objectMapper.writeValueAsBytes(manifest);
            String manifestSha256 = cryptoService.sha256Hex(manifestBytes);

            finalPackage = Files.createTempFile("comiverse-chapter-", ".cvpack");
            try (OutputStream rawOutput = Files.newOutputStream(finalPackage);
                 DataOutputStream output = new DataOutputStream(new BufferedOutputStream(rawOutput));
                 InputStream payloadInput = new BufferedInputStream(Files.newInputStream(encryptedPayload))) {
                output.write(MAGIC);
                output.writeInt(manifestBytes.length);
                output.write(manifestBytes);
                payloadInput.transferTo(output);
            }

            long packageSize = Files.size(finalPackage);
            if (packageSize > properties.getMaxPackageBytes()) {
                throw chapterTooLarge();
            }
            String packageSha256 = fileSha256(finalPackage);
            String wrappedContentKey = cryptoService.wrapContentKey(
                    contentKey,
                    cryptoService.parseDevicePublicKey(device.getPublicKeyBase64())
            );

            OfflinePackageEntity record = OfflinePackageEntity.builder()
                    .packageId(packageId)
                    .userId(device.getUserId())
                    .chapterId(chapter.getId())
                    .comicId(chapter.getComic().getId())
                    .offlineDeviceId(device.getId())
                    .deviceKeySha256(device.getPublicKeySha256())
                    .contentRevision(contentRevision)
                    .sourceDescriptorSha256(sourceDescriptor)
                    .manifestSha256(manifestSha256)
                    .packageSha256(packageSha256)
                    .packageSize(packageSize)
                    .wrappedContentKey(wrappedContentKey)
                    .keyAlgorithm(OfflineDownloadCryptoService.KEY_WRAP_ALGORITHM)
                    .formatVersion(FORMAT_VERSION)
                    .revoked(false)
                    .build();
            OfflinePackageEntity saved = packageRepository.save(record);
            Path deliverable = finalPackage;
            finalPackage = null;
            return new PreparedPackage(saved, new OfflinePackageArtifact(deliverable, packageSize));
        } catch (OfflineDownloadException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new OfflineDownloadException(
                    "OFFLINE_PACKAGE_FAILED",
                    "The protected offline package could not be created",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        } finally {
            Arrays.fill(contentKey, (byte) 0);
            deleteQuietly(encryptedPayload);
            deleteQuietly(finalPackage);
        }
    }

    public String sourceDescriptor(ChapterEntity chapter) {
        StringBuilder canonical = new StringBuilder();
        canonical.append("CV-SOURCE-V1\n")
                .append(chapter.getId()).append('\n')
                .append(chapter.getComic() == null ? "" : chapter.getComic().getId()).append('\n');
        String storedContentHash = chapter.getContentHash() == null ? "" : chapter.getContentHash().trim();
        canonical.append(storedContentHash.length()).append(':').append(storedContentHash).append('\n');
        List<String> images = chapter.getImages() == null ? List.of() : chapter.getImages();
        canonical.append(images.size()).append('\n');
        for (int index = 0; index < images.size(); index++) {
            String image = images.get(index) == null ? "" : images.get(index).trim();
            canonical.append(index + 1).append(':')
                    .append(image.getBytes(StandardCharsets.UTF_8).length).append(':')
                    .append(image).append('\n');
        }
        return cryptoService.sha256Hex(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private List<PageFingerprint> fingerprintPages(List<String> imageUrls) {
        List<PageFingerprint> result = new ArrayList<>();
        long totalBytes = 0L;
        for (int index = 0; index < imageUrls.size(); index++) {
            OfflineSourceImageService.DownloadedPage page = sourceImageService.download(imageUrls.get(index));
            byte[] plaintext = page.bytes();
            try {
                totalBytes += plaintext.length + 16L;
                if (totalBytes > properties.getMaxPackageBytes()) {
                    throw chapterTooLarge();
                }
                result.add(new PageFingerprint(
                        index + 1,
                        plaintext.length,
                        page.contentType(),
                        cryptoService.sha256Hex(plaintext)
                ));
            } finally {
                Arrays.fill(plaintext, (byte) 0);
            }
        }
        return result;
    }

    private List<PageManifestEntry> encryptPages(
            ChapterEntity chapter,
            OfflineDeviceEntity device,
            UUID packageId,
            String contentRevision,
            List<PageFingerprint> fingerprints,
            byte[] contentKey,
            Path encryptedPayload
    ) throws Exception {
        List<PageManifestEntry> entries = new ArrayList<>();
        long offset = 0L;
        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(encryptedPayload))) {
            for (int index = 0; index < chapter.getImages().size(); index++) {
                PageFingerprint expected = fingerprints.get(index);
                OfflineSourceImageService.DownloadedPage current = sourceImageService.download(chapter.getImages().get(index));
                byte[] plaintext = current.bytes();
                byte[] nonce = null;
                byte[] ciphertext = null;
                try {
                    String currentHash = cryptoService.sha256Hex(plaintext);
                    if (plaintext.length != expected.plaintextLength()
                            || !currentHash.equals(expected.plaintextSha256())) {
                        throw new OfflineDownloadException(
                                "CONTENT_CHANGED_RETRY",
                                "Chapter content changed while the offline package was being created. Please retry",
                                HttpStatus.CONFLICT
                        );
                    }

                    nonce = cryptoService.randomBytes(12);
                    byte[] aad = pageAad(
                            packageId,
                            device.getUserId(),
                            chapter.getId(),
                            device.getPublicKeySha256(),
                            contentRevision,
                            expected.pageNumber(),
                            expected.plaintextSha256()
                    );
                    ciphertext = cryptoService.encryptPage(plaintext, contentKey, nonce, aad);
                    output.write(ciphertext);
                    entries.add(new PageManifestEntry(
                            expected.pageNumber(),
                            offset,
                            ciphertext.length,
                            cryptoService.encodeUrl(nonce),
                            expected.contentType(),
                            expected.plaintextLength(),
                            expected.plaintextSha256(),
                            cryptoService.sha256Hex(ciphertext)
                    ));
                    offset += ciphertext.length;
                    if (offset > properties.getMaxPackageBytes()) {
                        throw chapterTooLarge();
                    }
                } finally {
                    Arrays.fill(plaintext, (byte) 0);
                    if (nonce != null) Arrays.fill(nonce, (byte) 0);
                    if (ciphertext != null) Arrays.fill(ciphertext, (byte) 0);
                }
            }
        }
        return entries;
    }

    private Map<String, Object> buildManifest(
            ChapterEntity chapter,
            OfflineDeviceEntity device,
            UUID packageId,
            String contentRevision,
            List<PageManifestEntry> entries
    ) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("version", FORMAT_VERSION);
        manifest.put("packageId", packageId.toString());
        manifest.put("userId", device.getUserId().toString());
        manifest.put("chapterId", chapter.getId().toString());
        manifest.put("comicId", chapter.getComic().getId().toString());
        manifest.put("deviceKeyId", device.getId().toString());
        manifest.put("deviceIdHash", device.getDeviceIdHash());
        manifest.put("deviceKeySha256", device.getPublicKeySha256());
        manifest.put("contentRevision", contentRevision);
        manifest.put("pageCount", entries.size());
        manifest.put("offsetBase", "PAYLOAD");
        manifest.put("cipher", "AES-256-GCM");
        manifest.put("tagLengthBits", 128);
        manifest.put("aadFormat", "CVPK1|packageId|userId|chapterId|deviceKeySha256|contentRevision|pageNumber|pageSha256");
        manifest.put("pages", entries);
        return manifest;
    }

    private String contentRevision(ChapterEntity chapter, List<PageFingerprint> fingerprints) {
        StringBuilder canonical = new StringBuilder();
        canonical.append("CV-CONTENT-V1\n")
                .append(chapter.getId()).append('\n')
                .append(chapter.getComic().getId()).append('\n')
                .append(fingerprints.size()).append('\n');
        for (PageFingerprint page : fingerprints) {
            canonical.append(page.pageNumber()).append(':')
                    .append(page.plaintextLength()).append(':')
                    .append(page.contentType()).append(':')
                    .append(page.plaintextSha256()).append('\n');
        }
        return cryptoService.sha256Hex(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private byte[] pageAad(
            UUID packageId,
            UUID userId,
            UUID chapterId,
            String deviceKeySha256,
            String contentRevision,
            int pageNumber,
            String pageSha256
    ) {
        return String.join("|",
                "CVPK1",
                packageId.toString(),
                userId.toString(),
                chapterId.toString(),
                deviceKeySha256,
                contentRevision,
                Integer.toString(pageNumber),
                pageSha256
        ).getBytes(StandardCharsets.UTF_8);
    }

    private void validateLimits(ChapterEntity chapter, Instant now, UUID userId) {
        List<String> images = chapter.getImages();
        if (images == null || images.isEmpty()) {
            throw new OfflineDownloadException(
                    "CHAPTER_HAS_NO_PAGES",
                    "This chapter has no downloadable pages",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
        if (images.size() > properties.getMaxPages()) {
            throw chapterTooLarge();
        }
        long recent = packageRepository.countByUserIdAndCreatedAtAfterAndDeletedFalse(
                userId,
                now.minus(Duration.ofHours(1))
        );
        if (recent >= Math.max(50, properties.getMaxPackagesPerHour())) {
            throw new OfflineDownloadException(
                    "DOWNLOAD_RATE_LIMITED",
                    "Too many offline packages were requested. Please try again later",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }
    }

    private String fileSha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
            input.transferTo(OutputStream.nullOutputStream());
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private OfflineDownloadException chapterTooLarge() {
        return new OfflineDownloadException(
                "CHAPTER_TOO_LARGE",
                "The chapter is too large for an offline package",
                HttpStatus.PAYLOAD_TOO_LARGE
        );
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            path.toFile().deleteOnExit();
        }
    }

    private record PageFingerprint(
            int pageNumber,
            int plaintextLength,
            String contentType,
            String plaintextSha256
    ) {
    }

    private record PageManifestEntry(
            int pageNumber,
            long offset,
            int length,
            String nonce,
            String contentType,
            int plaintextLength,
            String pageSha256,
            String ciphertextSha256
    ) {
    }

    public record PreparedPackage(OfflinePackageEntity record, OfflinePackageArtifact artifact) {
    }

    public static final class OfflinePackageArtifact implements AutoCloseable {
        private final Path path;
        private final long size;

        private OfflinePackageArtifact(Path path, long size) {
            this.path = path;
            this.size = size;
        }

        public long size() {
            return size;
        }

        public void copyTo(OutputStream outputStream) throws java.io.IOException {
            try (InputStream input = Files.newInputStream(path)) {
                input.transferTo(outputStream);
            }
        }

        @Override
        public void close() {
            try {
                Files.deleteIfExists(path);
            } catch (Exception ignored) {
                path.toFile().deleteOnExit();
            }
        }
    }
}
