package com.sep.comiverse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranslatorResponse {
    private UUID id;
    private String username;
    private String fullName;
    private String email;
    private String avatarUrl;
    private String initials;
}
