package com.sep.comiverse.dto;

import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChapterLiteDTO {
    private UUID id;
    private UUID comicId;
    private String chapterNumber;
    private String title;
    private Long viewCount;
    private Boolean isPremium;
    private Instant createdAt;
}
