package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @NotBlank(message = "Full name cannot be blank")
    private String fullName;

    private String avatarUrl;

    private String backgroundImageUrl;
}
