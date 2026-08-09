package com.sep.comiverse.dto.request;

import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaskRequest {
    private String title;
    private String status;
    private UUID assigneeId;
    private UUID chapterId;   // ← khớp đúng tên field frontend gửi lên
    private String dueDate;
    /** Optional total chapter remuneration. Server derives it from page count when omitted. */
    private BigDecimal chapterRewardUsd;
}