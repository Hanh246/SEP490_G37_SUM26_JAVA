package com.sep.comiverse.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "users")
@EqualsAndHashCode(callSuper = true)
public class UserEntity extends BaseEntity {

    @Column(name = "username", unique = true)
    private String username;

    @Column(name = "password")
    private String password;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "phone")
    private String phone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private RoleEntity role;

    @Builder.Default
    @Column(name = "provider", nullable = false)
    private String provider = "LOCAL"; // LOCAL, GOOGLE

    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "reset_token")
    private String resetToken;

    @Column(name = "reset_token_expires_at")
    private LocalDateTime resetTokenExpiresAt;

    @Column(name = "premium_plan")
    private String premiumPlan;

    @Column(name = "premium_expires_at")
    private LocalDateTime premiumExpiresAt;

    @Builder.Default
    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Convert(converter = com.sep.comiverse.entity.converter.VectorConverter.class)
    @Column(name = "user_vector", columnDefinition = "vector(768)")
    private float[] userVector;

    @Column(name = "vector_updated_at")
    private java.time.Instant vectorUpdatedAt;
}

