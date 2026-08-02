package com.sep.comiverse.entity;

import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.entity.enums.ComicPublicationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "comics", indexes = {
        @Index(name = "idx_comics_moderation_deleted", columnList = "moderation_status, deleted"),
        @Index(name = "idx_comics_author_deleted", columnList = "author_id, deleted")
})
@EqualsAndHashCode(callSuper = true, exclude = "genres")
@ToString(exclude = "genres")
public class ComicEntity extends BaseEntity {

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    /** Original/source language of this comic, not the author's personal language. */
    @Builder.Default
    @Column(name = "language", nullable = false, length = 100, columnDefinition = "varchar(100) default 'Unknown'")
    private String language = "Unknown";

    @Column(name = "minimum_age")
    private Integer minimumAge;

    @Column(name = "author_id")
    private UUID authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "publication_status")
    private ComicPublicationStatus publicationStatus;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 32)
    private ComicModerationStatus moderationStatus = ComicModerationStatus.DRAFT;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "cover")
    private String cover;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "comic_genre",
            joinColumns = @JoinColumn(name = "comic_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    private Set<GenreEntity> genres;

    @Builder.Default
    @Column(name = "view_count", nullable = false, columnDefinition = "bigint default 0")
    private Long viewCount = 0L;

    @Builder.Default
    @Column(name = "save_count", nullable = false, columnDefinition = "integer default 0")
    private Integer saveCount = 0;

    @Builder.Default
    @Column(name = "like_count", nullable = false, columnDefinition = "integer default 0")
    private Integer likeCount = 0;

    @Builder.Default
    @Column(name = "rating_average", nullable = false, columnDefinition = "double precision default 0.0")
    private Double ratingAverage = 0.0;

    @Builder.Default
    @Column(name = "rating_count", nullable = false, columnDefinition = "integer default 0")
    private Integer ratingCount = 0;

    @Column(name = "latest_chapter_number")
    private String latestChapterNumber;

    @Builder.Default
    @Column(name = "latest_chapter_update_at")
    private Instant lastChapterUpdatedAt = Instant.now();

    @Builder.Default
    @Column(name = "chapter_count", nullable = false, columnDefinition = "integer default 0")
    private Integer chapterCount = 0;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "genre_ids")
    private List<UUID> genreIds;

    @Convert(converter = com.sep.comiverse.entity.converter.VectorConverter.class)
    @Column(name = "summary_vector", columnDefinition = "vector")
    private float[] summaryVector;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private java.time.Instant approvedAt;

    @PrePersist
    protected void ensureModerationDefaults() {
        if (this.moderationStatus == null) {
            this.moderationStatus = ComicModerationStatus.DRAFT;
        }
        if (this.language == null || this.language.isBlank()) {
            this.language = "Unknown";
        }
    }
}
