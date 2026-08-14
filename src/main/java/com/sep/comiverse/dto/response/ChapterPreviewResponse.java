package com.sep.comiverse.dto.response;

import com.sep.comiverse.entity.enums.ChapterStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChapterPreviewResponse {
    private UUID id;
    private UUID comicId;
    private UUID authorId;
    private String chapterNumber;
    private String title;
    private ChapterStatus status;
    private String rejectionReason;
    private Integer pageCount;
    private Date createdAt;
    private Date updatedAt;
    private List<ChapterPageResponse> pages;
}
