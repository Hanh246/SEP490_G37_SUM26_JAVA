package com.sep.comiverse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "login_devices",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_login_device_user_identifier",
                columnNames = {"user_id", "device_id_hash"}
        ),
        indexes = @Index(name = "idx_login_device_user_revoked", columnList = "user_id,revoked,deleted")
)
@EqualsAndHashCode(callSuper = true)
public class LoginDeviceEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "device_id_hash", nullable = false, length = 64)
    private String deviceIdHash;

    @Column(name = "device_name", nullable = false, length = 120)
    private String deviceName;

    @Column(name = "platform", nullable = false, length = 20)
    private String platform;

    @Builder.Default
    @Column(name = "revoked", nullable = false)
    private Boolean revoked = false;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
}
