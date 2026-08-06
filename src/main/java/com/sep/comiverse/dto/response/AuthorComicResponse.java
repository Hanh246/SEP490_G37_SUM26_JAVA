package com.sep.comiverse.dto.response;

import com.sep.comiverse.entity.enums.ComicModerationStatus;
import com.sep.comiverse.entity.enums.ComicPublicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorComicResponse {
    private UUID id;
    private UUID authorId;
    private String title;
    private String summary;
    private String language;
    private Integer minimumAge;
    private String cover;
    private List<String> genres;
    private ComicPublicationStatus publicationStatus;
    private ComicModerationStatus moderationStatus;
    private Boolean isAppealed;
    private String appealReason;
    private String rejectionReason;
    private Long viewCount;
    private Integer saveCount;
    private Integer likeCount;
    private Double ratingAverage;
    private Integer ratingCount;
    private String latestChapterNumber;
    private Instant lastChapterUpdatedAt;
    private Integer chapterCount;
    private Instant createdAt;
    private Instant updatedAt;
}
