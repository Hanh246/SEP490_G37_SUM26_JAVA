package com.sep.comiverse.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChapterTranslationDTO {
    private UUID id;
    private UUID chapterId;
    private String chapterNumber;
    private UUID comicId;
    private String comicTitle;
    private String languageCode;
    private String pagesBubbles;
    private UUID projectTeamId;
    private Instant createdAt;
    private Instant updatedAt;
}