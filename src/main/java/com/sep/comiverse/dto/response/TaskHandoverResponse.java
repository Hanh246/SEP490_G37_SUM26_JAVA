package com.sep.comiverse.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskHandoverResponse {
    private UUID handoverId;
    private UUID taskId;
    private UUID fromTranslatorId;
    private UUID toTranslatorId;
    private List<Integer> completedPageNumbers;
    private Integer acceptedPageCount;
    private Integer reassignedPageCount;
    private BigDecimal responsibilityFactor;
    private String reason;
    private Instant handedOverAt;
}
