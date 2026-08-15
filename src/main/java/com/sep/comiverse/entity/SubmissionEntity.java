package com.sep.comiverse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.*;

import jakarta.persistence.Index;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "submissions", indexes = {
        @Index(name = "idx_submissions_author_queue_deleted", columnList = "author_id, queue_type, deleted"),
        @Index(name = "idx_submissions_status_deleted_created", columnList = "status, deleted, create_at"),
        @Index(name = "idx_submissions_queue_status_deleted", columnList = "queue_type, status, deleted"),
        @Index(name = "idx_submissions_comic_chapter_deleted", columnList = "comic_id, chapter_id, deleted")
})
@EqualsAndHashCode(callSuper = true)
public class SubmissionEntity extends BaseEntity {

    @Column(name = "comic_id")
    private UUID comicId;

    @Column(name = "chapter_id")
    private UUID chapterId;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "chapter")
    private String chapter;

    @Column(name = "submitted_by")
    private String submittedBy;

    @Column(name = "queue_type")
    private String queueType;

    @Column(name = "time_label")
    private String timeLabel;

    @Column(name = "timestamp")
    private Long timestamp;

    @Column(name = "words")
    private Integer words;

    @Column(name = "priority")
    private String priority;

    @Column(name = "flags")
    private Integer flags;

    @Column(name = "status")
    private String status;

    @Column(name = "cover")
    private String cover;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /** Immutable evidence snapshot captured when a chapter is submitted. */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "chapter_images")
    private List<String> chapterImages = new ArrayList<>();

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "moderator_id")
    private UUID moderatorId;

    /** Tracks which moderator has claimed this submission for review (IN_REVIEW state). */
    @Column(name = "reviewer_id")
    private UUID reviewerId;

    /** Timestamp when the reviewer claimed this submission. Used for auto-expiry of stale claims. */
    @Column(name = "review_started_at")
    private java.time.Instant reviewStartedAt;
}