package com.sep.comiverse.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Data
@Entity
@Transactional
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

    @Column(name = "chapter_id")
    private UUID chapterId;

    @Column(name = "title")
    private String title;

    @Column(name = "column_name")
    private String columnName;


    @Column(name = "progress")
    private Integer progress;

    @Column(name = "assignees")
    private String assignees; // Comma-separated list, e.g. "MC,SD"

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "due_date")
    private String dueDate;
}
