package com.sep.comiverse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.UUID;
import java.time.LocalDate;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private UUID userId;
    private String username;
    private String fullName;
    private String email;
    private String role;
    private LocalDate dateOfBirth;
    private String avatarUrl;
}
