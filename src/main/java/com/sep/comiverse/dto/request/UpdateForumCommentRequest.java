package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateForumCommentRequest {

    @NotBlank(message = "Comment content cannot be blank")
    @Size(max = 5000, message = "Comment content must not exceed 5000 characters")
    private String content;
}
