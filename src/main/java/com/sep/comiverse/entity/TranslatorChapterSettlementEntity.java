package com.sep.comiverse.entity;

import com.sep.comiverse.entity.enums.TranslatorSettlementStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "translator_chapter_settlements",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_translator_settlement_task_version",
                columnNames = {"task_id", "version_no"}
        ),
        indexes = {
                @Index(name = "idx_translator_settlement_month", columnList = "settlement_month"),
                @Index(name = "idx_translator_settlement_task", columnList = "task_id")
        }
)
public class TranslatorChapterSettlementEntity extends BaseEntity {

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "chapter_id", nullable = false)
    private UUID chapterId;

    @Column(name = "project_team_id", nullable = false)
    private UUID projectTeamId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "settlement_month", nullable = false, length = 7)
    private String settlementMonth;

    @Column(name = "settled_at", nullable = false)
    private Instant settledAt;

    @Column(name = "total_chapter_reward_usd", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalChapterRewardUsd;

    @Column(name = "total_pages", nullable = false)
    private Integer totalPages;

    @Column(name = "page_rate_usd", nullable = false, precision = 19, scale = 6)
    private BigDecimal pageRateUsd;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TranslatorSettlementStatus status = TranslatorSettlementStatus.ACTIVE;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reversal_reason", length = 1000)
    private String reversalReason;
}
