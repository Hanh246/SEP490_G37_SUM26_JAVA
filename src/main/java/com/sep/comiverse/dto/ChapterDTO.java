package com.sep.comiverse.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sep.comiverse.entity.enums.ChapterStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChapterDTO {
    private UUID id;

    private UUID comicId;

    private String chapterNumber;

    private String title;

    private ChapterStatus moderationStatus;

    private String rejectionReason;

    @Builder.Default
    private Long viewCount = 0L;

    @Builder.Default
    private Boolean isPremium = false;

    private Instant createdAt;

    @JsonProperty(access = JsonProperty.Access.AUTO)
    private List<String> images;

    private Integer pageCount;

    private String num;
    private String date;

    private String approvedBy;
    private Instant approvedAt;
    
    private String rejectedBy;
}
