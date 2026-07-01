package com.sep.comiverse.entity;

import com.sep.comiverse.entity.enums.ChapterStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

import java.util.Date;
import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "chapters", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"comic_id", "chapter_number"})
})
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"comic", "projectTeam"})
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

    @Column(name = "chapter_number", nullable = false, columnDefinition = "varchar(255) default '1'")
    private String chapterNumber;

    @Column(name = "title")
    private String title;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "images", columnDefinition = "text[]")
    private List<String> images;

    @Builder.Default
    @Column(name = "view_count", nullable = false, columnDefinition = "bigint default 0")
    private Long viewCount = 0L;

    @Builder.Default
    @Column(name = "is_premium", nullable = false, columnDefinition = "boolean default false")
    private Boolean isPremium = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_team_id")
    private ProjectTeamEntity projectTeam;
}
