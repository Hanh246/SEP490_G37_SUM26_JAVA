package com.sep.comiverse.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChapterCommentDTO {
    private UUID id;
    private UUID userId;
    private String userName;
    private String userAvatar;
    private UUID chapterId;
    private String content;
    private UUID parentId;
    private UUID mentionId;
    private String mentionName;
    private Instant createdAt;
    private Instant updatedAt;
}
