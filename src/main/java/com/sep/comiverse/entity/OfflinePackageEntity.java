package com.sep.comiverse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "offline_packages", indexes = {
        @Index(name = "idx_offline_package_user_created", columnList = "user_id,create_at"),
        @Index(name = "idx_offline_package_device_revoked", columnList = "offline_device_id,revoked")
})
public class OfflinePackageEntity extends BaseEntity {

    @Column(name = "package_id", nullable = false, unique = true)
    private UUID packageId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "chapter_id", nullable = false)
    private UUID chapterId;

    @Column(name = "comic_id", nullable = false)
    private UUID comicId;

    @Column(name = "offline_device_id", nullable = false)
    private UUID offlineDeviceId;

    @Column(name = "device_key_sha256", nullable = false, length = 64)
    private String deviceKeySha256;

    @Column(name = "content_revision", nullable = false, length = 64)
    private String contentRevision;

    @Column(name = "source_descriptor_sha256", nullable = false, length = 64)
    private String sourceDescriptorSha256;

    @Column(name = "manifest_sha256", nullable = false, length = 64)
    private String manifestSha256;

    @Column(name = "package_sha256", nullable = false, length = 64)
    private String packageSha256;

    @Column(name = "package_size", nullable = false)
    private Long packageSize;

    @Column(name = "wrapped_content_key", nullable = false, length = 2048)
    private String wrappedContentKey;

    @Column(name = "key_algorithm", nullable = false, length = 64)
    private String keyAlgorithm;

    @Column(name = "format_version", nullable = false)
    private Integer formatVersion;

    @Builder.Default
    @Column(name = "revoked", nullable = false)
    private Boolean revoked = false;
}
