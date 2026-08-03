package com.sep.comiverse.dto.request;

import com.sep.comiverse.entity.enums.AuthorType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorProfileRequest {

    private AuthorType authorType;

    @NotBlank(message = "Display name is required")
    @Size(max = 150, message = "Display name must not exceed 150 characters")
    private String displayName;

    @Size(max = 255, message = "Legal name must not exceed 255 characters")
    private String legalName;

    @Size(max = 3000, message = "Bio must not exceed 3000 characters")
    private String bio;

    @Size(max = 500, message = "Avatar URL must not exceed 500 characters")
    private String avatarUrl;

    @Email(message = "Contact email must be valid")
    @Size(max = 255, message = "Contact email must not exceed 255 characters")
    private String contactEmail;

    @Size(max = 100, message = "External profile reference must not exceed 100 characters")
    private String externalProfileRef;

    @Size(max = 2000, message = "Note must not exceed 2000 characters")
    private String note;
}
