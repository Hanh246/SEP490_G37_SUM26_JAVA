package com.sep.comiverse.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "team_join_requests")
public class TeamJoinRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "project_team_id", nullable = false)
    private UUID projectTeamId;

    @Column(name = "name")
    private String name;

    @Column(name = "time")
    private String time;

    @Column(name = "text", columnDefinition = "TEXT")
    private String text;

    @Column(name = "roles")
    private String roles; // Comma-separated list, e.g. "Translator,Proofreader"

    @Column(name = "avatar")
    private String avatar;
}
