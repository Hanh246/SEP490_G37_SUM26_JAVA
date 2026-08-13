package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ForumCategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(max = 60, message = "Category name must not exceed 60 characters")
    private String name;

    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Category color must be a six-digit hex color")
    private String color;
}
