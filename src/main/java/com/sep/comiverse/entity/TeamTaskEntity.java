package com.sep.comiverse.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_team_id", insertable = false, updatable = false)
    private ProjectTeamEntity projectTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id")
    private ChapterEntity chapter;

    @Column(name = "title")
    private String title;

    @Column(name = "status")
    private String status;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "due_date")
    private String dueDate;

    /** Actual Project Leader completion time used for monthly Translator payout. */
    @Column(name = "completed_at")
    private Instant completedAt;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "taskId", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PageTranslationEntity> pages = new ArrayList<>();
}