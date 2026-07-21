package com.sep.comiverse.entity;

import jakarta.persistence.*;
import lombok.*;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "review_comments")
public class ReviewCommentEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", nullable = false)
    private PageTranslationEntity page;

    @Column(name = "bubble_id")
    private String bubbleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private UserEntity author;

    @Column(name = "content", columnDefinition = "text", nullable = false)
    private String content;

    @Builder.Default
    @Column(name = "resolved", nullable = false)
    private Boolean resolved = false;
}