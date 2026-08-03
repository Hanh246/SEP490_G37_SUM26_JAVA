package com.sep.comiverse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TranslatorTaskRevenueResponse {
    private UUID taskId;
    private UUID chapterId;
    private String taskTitle;
    private String chapterNumber;
    private String chapterTitle;
    private Instant completedAt;
    private BigDecimal revenueVnd;
}
