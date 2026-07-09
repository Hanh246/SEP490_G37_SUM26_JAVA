package com.sep.comiverse.entity;

import com.sep.comiverse.constants.ComicStatus;
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
        @Index(name = "idx_comics_slug_deleted", columnList = "slug, deleted"),
        @Index(name = "idx_comics_moderation_deleted", columnList = "moderation_status, deleted")
})
@EqualsAndHashCode(callSuper = true, exclude = "genres")
@ToString(exclude = "genres")
public class ComicEntity extends BaseEntity {

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "slug")
    private String slug;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "minimum_age")
    private Integer minimumAge;

    @Column(name = "author_id")
    private UUID authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ComicStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "publication_status")
    private ComicPublicationStatus publicationStatus;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 32)
    private ComicModerationStatus moderationStatus = ComicModerationStatus.DRAFT;

    @Column(name = "cover")
    private String cover;

    @Column(name = "thumbnail")
    private String thumbnail;

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
    @Column(name = "genre_ids", columnDefinition = "uuid[]")
    private List<UUID> genreIds;

    /*
     * PostgreSQL column summary_vector is pgvector type: vector(384).
     * Do not map it as byte[] because Hibernate will insert it as bytea.
     * Current Author upload flow does not need to write this field.
     */
    @Transient
    private byte[] summaryVector;

    @PrePersist
    protected void ensureModerationDefaults() {
        if (this.moderationStatus == null) {
            this.moderationStatus = ComicModerationStatus.DRAFT;
        }
    }
}
    @Column(name = "summary_vector", columnDefinition = "vector(384)", insertable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.OTHER)
    private float[] summaryVector;
}
