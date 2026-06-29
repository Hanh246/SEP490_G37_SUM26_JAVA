package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChapterUploadRequest {

    private UUID authorId;

    @NotNull(message = "Chapter number is required")
    @Min(value = 1, message = "Chapter number must be at least 1")
    private Integer chapterNumber;

    private String title;
}
