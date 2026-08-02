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

    @Column(name = "requester_id")
    private UUID requesterId;

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

    @Column(name = "cv_url", length = 512)
    private String cvUrl;

    @Column(name = "cv_file_name")
    private String cvFileName;

    @Transient
    private Integer activeProjectsCount;

    @Transient
    private Integer activeTasksCount;
}
