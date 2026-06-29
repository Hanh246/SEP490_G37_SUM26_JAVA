package com.sep.comiverse.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthorComicUpdateRequest {

    private UUID authorId;

    private String title;
    private String slug;
    private String description;
    private String coverImageUrl;
    private List<String> genres;
    private String publicationStatus;
}
