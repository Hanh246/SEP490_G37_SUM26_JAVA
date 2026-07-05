package com.sep.comiverse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorComicPackageUploadResponse {
    private AuthorComicResponse comic;
    private List<ChapterPreviewResponse> chapters;
    private Integer chapterCount;
    private String message;
}
