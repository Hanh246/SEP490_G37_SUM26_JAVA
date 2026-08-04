package com.sep.comiverse.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class OfflineLicenseResponse {
    private UUID packageId;
    private UUID chapterId;
    private UUID comicId;
    private UUID deviceKeyId;
    private String licenseToken;
    private String wrappedContentKey;
    private String keyAlgorithm;
    private String signingKeyId;
    private String packageSha256;
    private String manifestSha256;
    private String contentRevision;
    private String deviceIdHash;
    private String deviceKeySha256;
    private String wrappedKeySha256;
    private Long packageSize;
    private Integer formatVersion;
    private Instant offlineUntil;
    private Instant renewAfter;
    private Instant serverTime;
}
