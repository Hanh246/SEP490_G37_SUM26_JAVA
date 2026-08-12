package com.sep.comiverse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "forum_threads")
@EqualsAndHashCode(callSuper = true)
public class ForumThreadEntity extends BaseEntity {

    @Column(name = "title")
    private String title;

    @Column(name = "author")
    private String author;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(name = "category")
    private String category;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_pinned")
    @Builder.Default
    private Boolean isPinned = false;

    @Column(name = "is_locked")
    @Builder.Default
    private Boolean isLocked = false;

    @Column(name = "is_reported")
    @Builder.Default
    private Boolean isReported = false;

    @Column(name = "report_reason")
    private String reportReason;

    @Column(name = "replies")
    @Builder.Default
    private Integer replies = 0;

    @Column(name = "views")
    @Builder.Default
    private Integer views = 0;
}
