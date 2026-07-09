package com.sep.comiverse.dto.response;

import com.sep.comiverse.entity.enums.ChapterStatus;
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
public class SubmitChapterReviewResponse {
    private UUID chapterId;
    private UUID comicId;
    private ChapterStatus status;
    private Date submittedAt;
    private String message;
}
