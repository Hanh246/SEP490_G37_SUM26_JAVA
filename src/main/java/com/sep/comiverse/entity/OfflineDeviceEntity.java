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
        name = "offline_devices",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_offline_device_user_identifier",
                columnNames = {"user_id", "device_id_hash"}
        ),
        indexes = @Index(name = "idx_offline_device_user_revoked", columnList = "user_id,revoked,deleted")
)
public class OfflineDeviceEntity extends BaseEntity {

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

    @Builder.Default
    @Column(name = "revoked", nullable = false)
    private Boolean revoked = false;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
}
