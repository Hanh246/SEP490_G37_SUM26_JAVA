package com.sep.comiverse.dto.request;

import com.sep.comiverse.entity.enums.ComicPublicationStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
    private String summary;

    @jakarta.validation.constraints.Size(max = 100, message = "Comic language must not exceed 100 characters")
    private String language;

    @Min(value = 0, message = "Minimum age cannot be negative")
    @Max(value = 21, message = "Minimum age cannot be greater than 21")
    private Integer minimumAge;

    private String cover;
    private List<String> genres;
    private ComicPublicationStatus publicationStatus;
}
