package com.sep.comiverse.dto;

import com.sep.comiverse.entity.enums.ChapterStatus;
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
    private ChapterStatus moderationStatus;
    private String rejectionReason;
    private UUID rejectedById;
    private UUID approvedById;
    private Instant approvedAt;
    private Integer pageCount;
    
    @Builder.Default
    private java.util.List<String> translatedLanguages = new java.util.ArrayList<>();
    
    public ChapterLiteDTO(UUID id, UUID comicId, String chapterNumber, String title,
                          Long viewCount, Boolean isPremium, Instant createdAt,
                          ChapterStatus moderationStatus, UUID approvedById, Instant approvedAt) {
        this.id = id;
        this.comicId = comicId;
        this.chapterNumber = chapterNumber;
        this.title = title;
        this.viewCount = viewCount;
        this.isPremium = isPremium;
        this.createdAt = createdAt;
        this.moderationStatus = moderationStatus;
        this.approvedById = approvedById;
        this.approvedAt = approvedAt;
        this.translatedLanguages = new java.util.ArrayList<>();
    }
}
