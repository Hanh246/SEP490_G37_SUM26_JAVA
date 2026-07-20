package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateComicCommentRequest {

    @NotNull(message = "Comic ID is required")
    private UUID comicId;

    @NotBlank(message = "Comment content cannot be blank")
    private String content;

    private UUID parentId;

    private UUID mentionId;
}
