package com.sep.comiverse.dto.request;

import lombok.*;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaskRequest {
    private String title;
    private String status;
    private List<UUID> assigneeIds;
    private UUID chapterId;   // ← khớp đúng tên field frontend gửi lên
    private String dueDate;
}