package com.sep.comiverse.entity;

import com.sep.comiverse.entity.enums.ChapterStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.Date;
import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "chapters")
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = "projectTeam")
public class ChapterEntity extends BaseEntity {

    @Column(name = "comic_id")
    private UUID comicId;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(name = "chapter_number")
    private Integer chapterNumber;

    @Column(name = "title")
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ChapterStatus status;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "submitted_at")
    private Date submittedAt;

    @Column(name = "approved_at")
    private Date approvedAt;

    @Column(name = "rejected_at")
    private Date rejectedAt;

    @Column(name = "moderation_note", columnDefinition = "TEXT")
    private String moderationNote;

    @Column(name = "num")
    private String num; // e.g. "Chapter 45"

    @Column(name = "date")
    private String date; // date/time representation or formatted relative label

    @Column(name = "words")
    private Integer words;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_team_id")
    private ProjectTeamEntity projectTeam;
}
