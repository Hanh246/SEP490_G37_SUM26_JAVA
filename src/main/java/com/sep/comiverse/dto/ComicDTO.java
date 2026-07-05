package com.sep.comiverse.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sep.comiverse.constants.ComicStatus;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComicDTO {
    private UUID id;
    private String title;
    private String slug;
    private String summary;
    private UUID authorId;
    private ComicStatus status;
    private ComicModerationStatus moderationStatus;
    private String cover;
    private String thumbnail;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Set<GenreDTO> genres;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private List<UUID> genreIds;

    private Long viewCount;
    private Integer saveCount;
    private Double ratingAverage;
    private Integer ratingCount;
    private String latestChapterNumber;
}
