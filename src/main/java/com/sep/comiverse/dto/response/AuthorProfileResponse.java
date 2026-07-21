package com.sep.comiverse.dto.response;

import com.sep.comiverse.entity.enums.AuthorType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorProfileResponse {
    private UUID id;
    private UUID userId;
    private String username;
    private String fullName;
    private String email;
    private AuthorType authorType;
    private String displayName;
    private String legalName;
    private String bio;
    private String language;
    private String avatarUrl;
    private String contactEmail;
    private String externalProfileRef;
    private String note;
}
