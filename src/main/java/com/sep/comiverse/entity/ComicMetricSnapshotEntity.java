package com.sep.comiverse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        name = "comic_metric_snapshots",
        indexes = @Index(name = "idx_metric_comic_author", columnList = "comic_id, author_id")
)
@EqualsAndHashCode(callSuper = true)
public class ComicMetricSnapshotEntity extends BaseEntity {

    @Column(name = "comic_id", nullable = false)
    private UUID comicId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "view_count")
    private Long viewCount;

    @Column(name = "saved_count")
    private Long savedCount;

    @Column(name = "like_count")
    private Long likeCount;

    @Column(name = "estimated_revenue")
    private BigDecimal estimatedRevenue;
}
