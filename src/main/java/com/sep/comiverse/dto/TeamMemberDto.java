package com.sep.comiverse.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemberDto {
    private UUID id;
    private String name;
    private String role;
    private String accountRole;
    private String avatar;
    private Boolean online;
    private Instant lastSeenAt;
    private Instant joinDate;
    private String cvUrl;
    private String bio;
    private Integer experienceYears;
    private Integer joinedProjectCount;
    private String phoneNumber;
    private String facebookUrl;
    private List<String> specializations;
    private Integer activeTaskCount;
}
