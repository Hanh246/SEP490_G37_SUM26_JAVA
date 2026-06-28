package com.sep.comiverse.entity;

import jakarta.persistence.*;
import lombok.*;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "notifications")
@EqualsAndHashCode(callSuper = true)
public class NotificationEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "type", nullable = false, length = 20)
    private String type; // INFO, WARNING, UPDATE, MAINTENANCE

    @Column(name = "broadcast_id")
    private java.util.UUID broadcastId;

    @Column(name = "target_roles", length = 100)
    private String targetRoles; // e.g. "ADMIN,STAFF" or "ALL"

    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;
}
