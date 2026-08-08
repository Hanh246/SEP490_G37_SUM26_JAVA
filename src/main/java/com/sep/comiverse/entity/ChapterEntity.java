package com.sep.comiverse.entity;

import com.sep.comiverse.entity.enums.ChapterStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "chapters", indexes = {
        @Index(name = "idx_chapters_comic_number_deleted", columnList = "comic_id, chapter_number, deleted"),
        @Index(name = "idx_chapters_moderation_deleted", columnList = "moderation_status, deleted"),
        @Index(name = "idx_chapters_comic_status_deleted", columnList = "comic_id, moderation_status, deleted")
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
    private ChapterStatus moderationStatus = ChapterStatus.PREVIEW_READY;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    /**
     * PostgreSQL text[] column storing chapter image URLs in reading order.
     * This replaces the old chapter_pages table.
     */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "images", nullable = false)
    private List<String> images = new ArrayList<>();

    @Builder.Default
    @Column(name = "view_count", nullable = false, columnDefinition = "bigint default 0")
    private Long viewCount = 0L;

    @Builder.Default
    @Column(name = "is_premium", nullable = false, columnDefinition = "boolean default false")
    private Boolean isPremium = false;

    @OneToMany(mappedBy = "chapter", fetch = FetchType.LAZY)
    @Builder.Default
    private List<TeamTaskEntity> tasks = new ArrayList<>();

    // projectTeam removed

    @Column(name = "approved_by_id")
    private UUID approvedById;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejected_by_id")
    private UUID rejectedById;

    @Column(name = "content_hash", length = 64)
    private String contentHash;
}
