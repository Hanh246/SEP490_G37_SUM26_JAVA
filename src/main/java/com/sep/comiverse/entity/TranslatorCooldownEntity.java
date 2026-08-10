package com.sep.comiverse.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "translator_cooldowns")
public class TranslatorCooldownEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** CANCEL, LEAVE, REJECT */
    @Column(name = "cooldown_type", nullable = false)
    private String cooldownType;

    @Column(name = "cooldown_until", nullable = false)
    private Instant cooldownUntil;

    /** The team related to this cooldown (nullable — null means global block) */
    @Column(name = "related_team_id")
    private UUID relatedTeamId;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}
