package com.sep.comiverse.dto.response;

import com.sep.comiverse.entity.enums.ComicStatus;
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
public class AuthorComicResponse {
    private UUID id;
    private UUID authorId;
    private String title;
    private String slug;
    private String description;
    private String coverImageUrl;
    private List<String> genres;
    private String publicationStatus;
    private ComicStatus status;
    private String moderationNote;
    private Integer chapters;
    private String views;
    private Date publishedAt;
    private Date createdAt;
    private Date updatedAt;
}
