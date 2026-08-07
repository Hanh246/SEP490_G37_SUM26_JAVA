package com.sep.comiverse.entity;

import com.sep.comiverse.entity.enums.PageStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
@Table(name = "page_translation")
public class PageTranslationEntity extends BaseEntity{
    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private TeamTaskEntity taskId;

    @Column(name = "image_url", nullable = false, columnDefinition = "text")
    private String imageUrl;

    /** Translator responsible for this page. This survives task-level handovers. */
    @Column(name = "assigned_translator_id")
    private UUID assignedTranslatorId;

    /** Responsibility coefficient K used when this page is settled. */
    @Builder.Default
    @Column(name = "responsibility_factor", nullable = false, precision = 4, scale = 2, columnDefinition = "numeric(4,2) default 1.00")
    private BigDecimal responsibilityFactor = BigDecimal.ONE.setScale(2);

    @Column(name = "completed_at")
    private Instant completedAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private PageStatus status = PageStatus.TODO;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bubbles", columnDefinition = "jsonb")
    private String bubbles = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "review_baseline_bubbles", columnDefinition = "jsonb")
    private String reviewBaselineBubbles;

    @PrePersist
    @PreUpdate
    protected void ensureDefaults() {
        if (status == null) {
            status = PageStatus.TODO;
        }
        if( bubbles == null) {
            bubbles = "[]";
        }
        if (responsibilityFactor == null) {
            responsibilityFactor = BigDecimal.ONE.setScale(2);
        }
    }
}
