package com.sep.comiverse.entity;

import com.sep.comiverse.entity.enums.ReportStatus;
import com.sep.comiverse.entity.enums.ReportTargetType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "reports", indexes = {
        @Index(name = "idx_reports_reporter_target", columnList = "reporter_id, target_type, target_id, status"),
        @Index(name = "idx_reports_status", columnList = "status, deleted"),
        @Index(name = "idx_reports_category", columnList = "category_id, deleted"),
        @Index(name = "idx_reports_handler", columnList = "handler_id, deleted"),
        @Index(name = "idx_reports_created_at", columnList = "create_at")
})
@EqualsAndHashCode(callSuper = true, exclude = {"reporter", "category", "handler"})
@ToString(exclude = {"reporter", "category", "handler"})
public class ReportEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private UserEntity reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private ReportCategoryEntity category;

    @Column(name = "description_text", columnDefinition = "TEXT")
    private String descriptionText;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ReportStatus status = ReportStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handler_id")
    private UserEntity handler;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
