package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateProfileRequest {
    @NotBlank(message = "Full name cannot be blank")
    private String fullName;

    private String avatarUrl;

    private String backgroundImageUrl;

    private LocalDate dateOfBirth;

    @Size(max = 1000, message = "Bio cannot exceed 1000 characters")
    private String bio;
}
