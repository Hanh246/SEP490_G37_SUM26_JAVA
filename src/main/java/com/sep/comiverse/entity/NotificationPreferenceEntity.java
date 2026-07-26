package com.sep.comiverse.entity;

import com.sep.comiverse.entity.enums.NotificationPreferenceKey;
import jakarta.persistence.*;
import lombok.*;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "notification_preferences",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_preference_user_key",
                columnNames = {"user_id", "preference_key"}
        )
)
public class NotificationPreferenceEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "preference_key", nullable = false, length = 50)
    private NotificationPreferenceKey preferenceKey;

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
}
