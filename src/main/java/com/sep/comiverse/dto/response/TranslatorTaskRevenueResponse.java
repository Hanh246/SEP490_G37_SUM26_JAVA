package com.sep.comiverse.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranslatorTaskRevenueResponse {
    private UUID settlementId;
    private Integer settlementVersion;
    private UUID taskId;
    private UUID chapterId;
    private String taskTitle;
    private String chapterNumber;
    private String chapterTitle;
    private Instant completedAt;
    private Instant settledAt;
    private Integer completedPageCount;
    private Integer totalPageCount;
    private BigDecimal pageRateUsd;
    private BigDecimal grossBeforeFactorUsd;
    private BigDecimal averageResponsibilityFactor;
    private BigDecimal adjustmentUsd;
    private BigDecimal revenueUsd;
    private String rowType;
    private String note;
}
