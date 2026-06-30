package com.sep.comiverse.entity;

import com.sep.comiverse.constants.ComicStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "comics")
@EqualsAndHashCode(callSuper = true, exclude = "genres")
@ToString(exclude = "genres")
public class ComicEntity extends BaseEntity {

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "slug", unique = true)
    private String slug;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "author_id")
    private UUID authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ComicStatus status;

    @Column(name = "cover")
    private String cover; // Cover emoji or image path (e.g. "⚔️")

    @Column(name = "thumbnail")
    private String thumbnail;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "comic_genre",
            joinColumns = @JoinColumn(name = "comic_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    private Set<GenreEntity> genres;

    //fast query
    @Builder.Default
    @Column(name = "view_count", nullable = false, columnDefinition = "bigint default 0")
    private Long viewCount = 0L;

    @Builder.Default
    @Column(name = "save_count", nullable = false, columnDefinition = "integer default 0")
    private Integer saveCount = 0;

    @Builder.Default
    @Column(name = "rating_average", nullable = false, columnDefinition = "double precision default 0.0")
    private Double ratingAverage = 0.0;

    @Builder.Default
    @Column(name = "rating_count", nullable = false, columnDefinition = "integer default 0")
    private Integer ratingCount = 0;

    @Column(name = "latest_chapter_number")
    private String latestChapterNumber;

    //Recommendation
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "genre_ids", columnDefinition = "uuid[]")
    private List<UUID> genreIds;

    @Column(name = "summary_vector", columnDefinition = "vector(384)")
    @JdbcTypeCode(SqlTypes.OTHER)
    private float[] summaryVector;
}
