package com.sep.comiverse.dto.request;

import com.sep.comiverse.entity.enums.ComicPublicationStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthorComicCreateRequest {

    private UUID authorId;

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Description is required")
    private String summary;

    @NotBlank(message = "Comic language is required")
    @jakarta.validation.constraints.Size(max = 100, message = "Comic language must not exceed 100 characters")
    private String language;

    @NotNull(message = "Minimum age is required")
    @Min(value = 0, message = "Minimum age cannot be negative")
    @Max(value = 21, message = "Minimum age cannot be greater than 21")
    private Integer minimumAge;

    @NotBlank(message = "Cover image is required")
    private String cover;

    @NotEmpty(message = "At least one genre is required")
    private List<String> genres;
    
    @NotNull(message = "Publication status is required")
    private ComicPublicationStatus publicationStatus;
}
