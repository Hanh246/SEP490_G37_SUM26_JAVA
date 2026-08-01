package com.sep.comiverse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private UUID userId;
    private String username;
    private String fullName;
    private String email;
    private String role;
    private String avatarUrl;
    private String backgroundImageUrl;
    private LocalDate dateOfBirth;
    private String bio;
    private String premiumPlan;
    private LocalDateTime premiumExpiresAt;
    private Boolean premiumActive;
    private java.util.List<String> assignedLanguages;
}
