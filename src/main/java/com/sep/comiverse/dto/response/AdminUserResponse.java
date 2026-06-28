package com.sep.comiverse.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserResponse {
    @JsonProperty("id")
    private UUID id;

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("username")
    private String username;

    @JsonProperty("fullName")
    private String fullName;

    @JsonProperty("email")
    private String email;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("role")
    private String role;

    @JsonProperty("status")
    private String status;

    @JsonProperty("provider")
    private String provider;

    @JsonProperty("avatarUrl")
    private String avatarUrl;

    @JsonProperty("createdDate")
    private Date createdDate;

    @JsonProperty("updatedDate")
    private Date updatedDate;
}
