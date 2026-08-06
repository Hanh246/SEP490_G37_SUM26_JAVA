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
        name = "translator_earning_adjustments",
        indexes = {
                @Index(name = "idx_translator_adjustment_user_month", columnList = "translator_id, adjustment_month"),
                @Index(name = "idx_translator_adjustment_task", columnList = "task_id")
        }
)
public class TranslatorEarningAdjustmentEntity extends BaseEntity {

    @Column(name = "translator_id", nullable = false)
    private UUID translatorId;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "settlement_id", nullable = false)
    private UUID settlementId;

    @Column(name = "adjustment_month", nullable = false, length = 7)
    private String adjustmentMonth;

    @Column(name = "amount_usd", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountUsd;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;
}
