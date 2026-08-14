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
    private String authorName;
    private ComicPublicationStatus publicationStatus;
    private ComicModerationStatus moderationStatus;
    private Boolean isAppealed;
    private Boolean isModEdited;
    private String previousStateSnapshot;
    private String appealReason;
    private String rejectionReason;
    private String cover;
    private Set<GenreDTO> genres;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private List<UUID> genreIds;

    private Long viewCount;
    private Integer saveCount;
    private Integer likeCount;
    private Double ratingAverage;
    private Integer ratingCount;
    private String latestChapterNumber;
    private Instant createdAt;
    private Instant updatedAt;
    private Integer chapterCount;
    private Integer rejectedChapterCount;
    private Integer pendingChapterCount;
    private java.time.Instant lastChapterUpdatedAt;
    private String approvedBy;
    private java.time.Instant approvedAt;
}
