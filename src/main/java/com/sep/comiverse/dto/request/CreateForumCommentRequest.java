package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateForumCommentRequest {

    @NotBlank(message = "Comment content cannot be blank")
    @Size(max = 5000000, message = "Comment content must not exceed 5MB characters")
    private String content;

    private UUID parentId;
}
