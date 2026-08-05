package com.sep.comiverse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorUploadTaskResponse {
    private UUID taskId;
    private UUID authorId;
    private String type;
    private String status;
    private Integer progress;
    private String message;
    private String error;
    private ChapterPreviewResponse chapter;
    private Date createdAt;
    private Date updatedAt;
}
