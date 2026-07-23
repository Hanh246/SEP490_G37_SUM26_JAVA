package com.sep.comiverse.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.entity.enums.ComicPublicationStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComicDTO {
    private UUID id;
    private String title;
    private String summary;
    private String language;
    private Integer minimumAge;
    private UUID authorId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String authorName;
    private ComicPublicationStatus publicationStatus;
    private ComicModerationStatus moderationStatus;
    private String cover;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Set<GenreDTO> genres;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private List<UUID> genreIds;

    private Long viewCount;
    private Integer saveCount;
    private Integer likeCount;
    private Double ratingAverage;
    private Integer ratingCount;
    private String latestChapterNumber;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Instant createdAt;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Instant updatedAt;
    private Integer chapterCount;
    private java.time.Instant lastChapterUpdatedAt;
}
