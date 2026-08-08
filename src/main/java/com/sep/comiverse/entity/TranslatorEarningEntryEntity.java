package com.sep.comiverse.entity;

import com.sep.comiverse.entity.enums.TranslatorEarningEntryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Unified translator earning ledger.
 * PAGE_EARNING rows replace translator_page_earnings; signed adjustment rows
 * replace translator_earning_adjustments.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "translator_earning_entries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_translator_earning_settlement_page",
                columnNames = {"settlement_id", "page_id"}
        ),
        indexes = {
                @Index(name = "idx_translator_earning_user_month", columnList = "translator_id, entry_month"),
                @Index(name = "idx_translator_earning_task", columnList = "task_id"),
                @Index(name = "idx_translator_earning_settlement", columnList = "settlement_id"),
                @Index(name = "idx_translator_earning_type", columnList = "entry_type")
        }
)
public class TranslatorEarningEntryEntity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 40)
    private TranslatorEarningEntryType entryType;

    @Column(name = "translator_id", nullable = false)
    private UUID translatorId;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "settlement_id", nullable = false)
    private UUID settlementId;

    @Column(name = "chapter_id")
    private UUID chapterId;

    @Column(name = "page_id")
    private UUID pageId;

    @Column(name = "page_number")
    private Integer pageNumber;

    /** YYYY-MM accounting month. */
    @Column(name = "entry_month", nullable = false, length = 7)
    private String entryMonth;

    /** Only meaningful for PAGE_EARNING rows. */
    @Column(name = "responsibility_factor", precision = 4, scale = 2)
    private BigDecimal responsibilityFactor;

    /** Pre-K amount for page rows; null for standalone adjustments. */
    @Column(name = "gross_amount_usd", precision = 19, scale = 2)
    private BigDecimal grossAmountUsd;

    /** Signed ledger amount. Positive earns money; negative reverses/penalizes. */
    @Column(name = "amount_usd", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountUsd;

    @Column(name = "reason", length = 1000)
    private String reason;
}
