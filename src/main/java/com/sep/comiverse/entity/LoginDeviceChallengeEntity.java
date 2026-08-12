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
@Table(
        name = "login_device_challenges",
        uniqueConstraints = @UniqueConstraint(name = "uk_login_device_challenge", columnNames = "challenge_id"),
        indexes = @Index(name = "idx_login_device_challenge_user_created", columnList = "user_id,create_at")
)
@EqualsAndHashCode(callSuper = true)
public class LoginDeviceChallengeEntity extends BaseEntity {

    @Column(name = "challenge_id", nullable = false)
    private UUID challengeId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "operation", nullable = false, length = 24)
    private String operation;

    @Column(name = "device_id_hash", length = 64)
    private String deviceIdHash;

    @Column(name = "device_name", length = 120)
    private String deviceName;

    @Column(name = "platform", length = 20)
    private String platform;

    @Column(name = "target_device_id")
    private UUID targetDeviceId;

    @Column(name = "otp_hash", nullable = false, length = 64)
    private String otpHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Builder.Default
    @Column(name = "consumed", nullable = false)
    private Boolean consumed = false;
}
