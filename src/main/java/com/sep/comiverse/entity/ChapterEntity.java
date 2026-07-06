package com.sep.comiverse.entity;

import com.sep.comiverse.entity.enums.ChapterStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "chapters", indexes = {
        @Index(name = "idx_chapters_comic_number_deleted", columnList = "comic_id, chapter_number, deleted")
})
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"comic", "projectTeam"})
public class ChapterEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comic_id")
    private ComicEntity comic;

    @Builder.Default
    @Column(name = "chapter_number", nullable = false)
    private String chapterNumber = "1";

    @Column(name = "title")
    private String title;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 32)
    private ChapterStatus moderationStatus = ChapterStatus.DRAFT;

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