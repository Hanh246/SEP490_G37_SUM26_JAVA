package com.sep.comiverse.controller;

import com.sep.comiverse.config.OfflineDownloadProperties;
import com.sep.comiverse.dto.request.CreateOfflineChapterPackageRequest;
import com.sep.comiverse.dto.request.CreateOfflineDeviceChallengeRequest;
import com.sep.comiverse.dto.request.EnrollOfflineDeviceRequest;
import com.sep.comiverse.dto.response.BaseResponse;
import com.sep.comiverse.dto.response.OfflineDeviceChallengeResponse;
import com.sep.comiverse.dto.response.OfflineDeviceResponse;
import com.sep.comiverse.dto.response.OfflineLicenseResponse;
import com.sep.comiverse.entity.OfflinePackageEntity;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.OfflineChapterDownloadService;
import com.sep.comiverse.service.OfflineDeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/downloads")
@PreAuthorize("hasAuthority('READER')")
@Tag(name = "Reader - Offline Downloads", description = "Protected Premium chapter downloads and device licenses")
public class OfflineDownloadController {

    public static final String HEADER_LICENSE = "X-Comiverse-License";
    public static final String HEADER_WRAPPED_KEY = "X-Comiverse-Wrapped-Key";
    public static final String HEADER_KEY_ALGORITHM = "X-Comiverse-Key-Algorithm";
    public static final String HEADER_EXPIRES_AT = "X-Comiverse-License-Expires-At";
    public static final String HEADER_SERVER_TIME = "X-Comiverse-Server-Time";
    public static final String HEADER_PACKAGE_SHA256 = "X-Comiverse-Package-Sha256";
    public static final String HEADER_MANIFEST_SHA256 = "X-Comiverse-Manifest-Sha256";
    public static final String HEADER_PACKAGE_ID = "X-Comiverse-Package-Id";
    public static final String HEADER_DEVICE_KEY_ID = "X-Comiverse-Device-Key-Id";
    public static final String HEADER_FORMAT_VERSION = "X-Comiverse-Format-Version";
    public static final String HEADER_SIGNING_KEY_ID = "X-Comiverse-Signing-Key-Id";
    public static final MediaType CVPACK_MEDIA_TYPE = MediaType.parseMediaType("application/vnd.comiverse.cvpack");

    private final OfflineDeviceService deviceService;
    private final OfflineChapterDownloadService downloadService;
    private final OfflineDownloadProperties properties;

    public OfflineDownloadController(
            OfflineDeviceService deviceService,
            OfflineChapterDownloadService downloadService,
            OfflineDownloadProperties properties
    ) {
        this.deviceService = deviceService;
        this.downloadService = downloadService;
        this.properties = properties;
    }

    @PostMapping("/devices/challenges")
    @Operation(summary = "Create an offline device proof-of-possession challenge")
    public ResponseEntity<BaseResponse<OfflineDeviceChallengeResponse>> createDeviceChallenge(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateOfflineDeviceChallengeRequest request
    ) {
        OfflineDeviceChallengeResponse data = deviceService.createChallenge(principal.getId(), request);
        return ResponseEntity.ok(BaseResponse.<OfflineDeviceChallengeResponse>builder()
                .success(true)
                .data(data)
                .build());
    }

    @PostMapping("/devices")
    @Operation(summary = "Enroll an offline device after proving possession of its private key")
    public ResponseEntity<BaseResponse<OfflineDeviceResponse>> enrollDevice(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody EnrollOfflineDeviceRequest request
    ) {
        OfflineDeviceResponse data = deviceService.enroll(principal.getId(), request);
        return ResponseEntity.ok(BaseResponse.<OfflineDeviceResponse>builder()
                .success(true)
                .data(data)
                .message("Offline device enrolled")
                .build());
    }

    @GetMapping("/devices")
    @Operation(summary = "List offline devices registered to the signed-in account")
    public ResponseEntity<BaseResponse<List<OfflineDeviceResponse>>> listDevices(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(BaseResponse.<List<OfflineDeviceResponse>>builder()
                .success(true)
                .data(deviceService.list(principal.getId()))
                .build());
    }

    @DeleteMapping("/devices/{deviceKeyId}")
    @Operation(summary = "Revoke an offline device and its online-renewable licenses")
    public ResponseEntity<BaseResponse<Void>> revokeDevice(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID deviceKeyId
    ) {
        deviceService.revoke(principal.getId(), deviceKeyId);
        return ResponseEntity.ok(BaseResponse.<Void>builder()
                .success(true)
                .message("Offline device revoked")
                .build());
    }

    @PostMapping(value = "/chapters/{chapterId}", produces = "application/vnd.comiverse.cvpack")
    @Operation(summary = "Create and download a protected offline chapter package")
    public ResponseEntity<StreamingResponseBody> downloadChapter(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID chapterId,
            @Valid @RequestBody CreateOfflineChapterPackageRequest request
    ) {
        OfflineChapterDownloadService.ProtectedChapterPackage protectedPackage = downloadService.createPackage(
                principal.getId(),
                chapterId,
                request.getDeviceKeyId()
        );
        OfflinePackageEntity record = protectedPackage.record();
        OfflineLicenseResponse metadata = protectedPackage.metadata();

        StreamingResponseBody body = outputStream -> {
            try (var artifact = protectedPackage.artifact()) {
                artifact.copyTo(outputStream);
            }
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(CVPACK_MEDIA_TYPE);
        headers.setContentLength(record.getPackageSize());
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("chapter-" + chapterId + ".cvpack")
                .build());
        headers.setCacheControl(CacheControl.noStore().cachePrivate().getHeaderValue());
        headers.setPragma("no-cache");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set(HEADER_LICENSE, metadata.getLicenseToken());
        headers.set(HEADER_WRAPPED_KEY, metadata.getWrappedContentKey());
        headers.set(HEADER_KEY_ALGORITHM, metadata.getKeyAlgorithm());
        headers.set(HEADER_EXPIRES_AT, metadata.getOfflineUntil().toString());
        headers.set(HEADER_SERVER_TIME, metadata.getServerTime().toString());
        headers.set(HEADER_PACKAGE_SHA256, metadata.getPackageSha256());
        headers.set(HEADER_MANIFEST_SHA256, record.getManifestSha256());
        headers.set(HEADER_PACKAGE_ID, metadata.getPackageId().toString());
        headers.set(HEADER_DEVICE_KEY_ID, metadata.getDeviceKeyId().toString());
        headers.set(HEADER_FORMAT_VERSION, metadata.getFormatVersion().toString());
        headers.set(HEADER_SIGNING_KEY_ID, properties.getSigningKeyId());
        headers.set(
                HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                String.join(", ",
                        HEADER_LICENSE,
                        HEADER_WRAPPED_KEY,
                        HEADER_KEY_ALGORITHM,
                        HEADER_EXPIRES_AT,
                        HEADER_SERVER_TIME,
                        HEADER_PACKAGE_SHA256,
                        HEADER_MANIFEST_SHA256,
                        HEADER_PACKAGE_ID,
                        HEADER_DEVICE_KEY_ID,
                        HEADER_FORMAT_VERSION,
                        HEADER_SIGNING_KEY_ID,
                        HttpHeaders.CONTENT_DISPOSITION
                )
        );
        return ResponseEntity.ok().headers(headers).body(body);
    }

    @PostMapping("/packages/{packageId}/licenses")
    @Operation(summary = "Renew a seven-day offline license without re-downloading the package")
    public ResponseEntity<BaseResponse<OfflineLicenseResponse>> renewLicense(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID packageId,
            @Valid @RequestBody CreateOfflineChapterPackageRequest request
    ) {
        OfflineLicenseResponse data = downloadService.renewLicense(
                principal.getId(),
                packageId,
                request.getDeviceKeyId()
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(BaseResponse.<OfflineLicenseResponse>builder()
                        .success(true)
                        .data(data)
                        .build());
    }
}
