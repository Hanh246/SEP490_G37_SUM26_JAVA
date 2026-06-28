package com.sep.comiverse.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "team_tasks")
public class TeamTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "project_team_id", nullable = false)
    private UUID projectTeamId;

    @Column(name = "title")
    private String title;

    @Column(name = "column_name")
    private String columnName; // backlog, in_progress, under_review, completed, paused

    @Column(name = "progress")
    private Integer progress;

    @Column(name = "assignees")
    private String assignees; // Comma-separated list, e.g. "MC,SD"

    @Column(name = "due_date")
    private String dueDate;
}
