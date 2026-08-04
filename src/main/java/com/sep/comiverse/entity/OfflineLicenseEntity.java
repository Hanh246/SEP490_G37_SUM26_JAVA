package com.sep.comiverse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "offline_licenses",
        uniqueConstraints = @UniqueConstraint(name = "uk_offline_license_jti", columnNames = "license_id"),
        indexes = {
                @Index(name = "idx_offline_license_user_created", columnList = "user_id,create_at"),
                @Index(name = "idx_offline_license_device_expiry", columnList = "offline_device_id,expires_at,revoked")
        }
)
public class OfflineLicenseEntity extends BaseEntity {

    @Column(name = "license_id", nullable = false, unique = true)
    private UUID licenseId;

    @Column(name = "package_id", nullable = false)
    private UUID packageId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "chapter_id", nullable = false)
    private UUID chapterId;

    @Column(name = "offline_device_id", nullable = false)
    private UUID offlineDeviceId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Builder.Default
    @Column(name = "revoked", nullable = false)
    private Boolean revoked = false;
}
