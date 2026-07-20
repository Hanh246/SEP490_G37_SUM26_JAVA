package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateChapterCommentRequest {

    @NotNull(message = "Chapter ID is required")
    private UUID chapterId;

    @NotBlank(message = "Comment content cannot be blank")
    private String content;

    private UUID parentId;

    private UUID mentionId;
}
