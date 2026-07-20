package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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

    private String slug;
    private String description;

    @Min(value = 0, message = "Minimum age cannot be negative")
    @Max(value = 21, message = "Minimum age cannot be greater than 21")
    private Integer minimumAge;
    private String coverImageUrl;
    private List<String> genres;
    private String publicationStatus;
}
