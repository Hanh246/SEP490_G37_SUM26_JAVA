package com.sep.comiverse.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
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

    @Builder.Default
    private Long viewCount = 0L;

    @Builder.Default
    private Boolean isPremium = false;

    private LocalDateTime createdAt;

    @JsonProperty(access = JsonProperty.Access.AUTO)
    private List<String> images;

    private String num;
    private String date;
}
