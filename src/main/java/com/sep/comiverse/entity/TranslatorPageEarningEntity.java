package com.sep.comiverse.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "translator_page_earnings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_translator_page_earning_settlement_page",
                columnNames = {"settlement_id", "page_id"}
        ),
        indexes = {
                @Index(name = "idx_translator_page_earning_user_month", columnList = "translator_id, settlement_month"),
                @Index(name = "idx_translator_page_earning_task", columnList = "task_id")
        }
)
public class TranslatorPageEarningEntity extends BaseEntity {

    @Column(name = "settlement_id", nullable = false)
    private UUID settlementId;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "chapter_id", nullable = false)
    private UUID chapterId;

    @Column(name = "page_id", nullable = false)
    private UUID pageId;

    @Column(name = "page_number", nullable = false)
    private Integer pageNumber;

    @Column(name = "translator_id", nullable = false)
    private UUID translatorId;

    @Column(name = "settlement_month", nullable = false, length = 7)
    private String settlementMonth;

    @Column(name = "responsibility_factor", nullable = false, precision = 4, scale = 2)
    private BigDecimal responsibilityFactor;

    @Column(name = "gross_amount_usd", nullable = false, precision = 19, scale = 2)
    private BigDecimal grossAmountUsd;

    @Column(name = "net_amount_usd", nullable = false, precision = 19, scale = 2)
    private BigDecimal netAmountUsd;
}
