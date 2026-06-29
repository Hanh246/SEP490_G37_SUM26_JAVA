package com.sep.comiverse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;
import lombok.*;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "submissions")
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
    private String queueType; // "author" or "translator"

    @Column(name = "time_label")
    private String timeLabel; // e.g. "2 hours ago"

    @Column(name = "timestamp")
    private Long timestamp; // milliseconds for sorting

    @Column(name = "words")
    private Integer words;

    @Column(name = "priority")
    private String priority; // High, Medium, Low

    @Column(name = "flags")
    private Integer flags;

    @Column(name = "status")
    private String status; // pending, approved, rejected

    @Column(name = "cover")
    private String cover; // e.g. "⚔️"

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;
}
