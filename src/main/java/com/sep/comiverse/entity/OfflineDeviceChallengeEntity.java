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
        name = "offline_device_challenges",
        uniqueConstraints = @UniqueConstraint(name = "uk_offline_device_challenge", columnNames = "challenge_id"),
        indexes = @Index(name = "idx_offline_challenge_user_created", columnList = "user_id,create_at")
)
public class OfflineDeviceChallengeEntity extends BaseEntity {

    @Column(name = "challenge_id", nullable = false, unique = true)
    private UUID challengeId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_id_hash", nullable = false, length = 64)
    private String deviceIdHash;

    @Column(name = "device_name", length = 120)
    private String deviceName;

    @Column(name = "public_key_sha256", nullable = false, length = 64)
    private String publicKeySha256;

    @Column(name = "public_key_base64", nullable = false, length = 8192)
    private String publicKeyBase64;

    @Column(name = "challenge_base64", nullable = false, length = 2048)
    private String challengeBase64;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Builder.Default
    @Column(name = "consumed", nullable = false)
    private Boolean consumed = false;
}
