package com.sep.comiverse.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "project_teams")
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = "chaptersList")
public class ProjectTeamEntity extends BaseEntity {

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "comic_name")
    private String comicName;

    @Column(name = "status")
    private String status; // Active, Paused, Completed

    @Column(name = "members_count")
    private Integer membersCount;

    @Column(name = "chapters_count")
    private Integer chaptersCount;

    @Column(name = "progress")
    private Integer progress; // percentage, e.g. 75

    @Column(name = "leader_name")
    private String leaderName;

    @Column(name = "leader_id")
    private UUID leaderId;

    @Column(name = "leader_initials")
    private String leaderInitials;

    @Column(name = "deadline")
    private String deadline; // date string or "unspecified"

    @Column(name = "source_lang")
    private String sourceLang;

    @Column(name = "target_lang")
    private String targetLang;

    @Column(name = "priority")
    private String priority; // High, Medium, Low

    @Column(name = "cover")
    private String cover; // Cover emoji or image path (e.g. "🔮")

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @ManyToMany
    @JoinTable(
            name = "team_members",
            joinColumns = @JoinColumn(name = "team_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private List<UserEntity> members = new ArrayList<>();

    @Column(name = "is_recruiting")
    @Builder.Default
    private Boolean isRecruiting = false;

    @Column(name = "max_members")
    @Builder.Default
    private Integer maxMembers = 5;

    @OneToMany(mappedBy = "projectTeam", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @org.hibernate.annotations.BatchSize(size = 100)
    @Builder.Default
    private List<ChapterEntity> chaptersList = new ArrayList<>();
}
