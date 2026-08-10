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
@Table(name = "team_join_bans", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"project_team_id", "user_id"})
})
public class TeamJoinBanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "project_team_id", nullable = false)
    private UUID projectTeamId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "banned_by")
    private UUID bannedBy;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}
