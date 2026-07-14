package com.sep.comiverse.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    @Column(name = "status")
    private String status;

    @Column(name = "assignee_ids")
    private List<UUID> assigneeIds;

    @Column(name = "due_date")
    private String dueDate;
}