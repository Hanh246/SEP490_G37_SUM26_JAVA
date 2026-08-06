package com.sep.comiverse.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForumCommentDTO {
    private UUID id;
    private UUID threadId;
    private UUID userId;
    private String author;
    private String avatarUrl;
    private String content;
    private UUID parentId;
    private Instant createdAt;

    @Builder.Default
    @JsonProperty("likesCount")
    private Integer likesCount = 0;

    @JsonProperty("isLikedByCurrentUser")
    private Boolean isLikedByCurrentUser;

    @JsonProperty("likedByCurrentUser")
    public Boolean getLikedByCurrentUser() {
        return isLikedByCurrentUser != null && isLikedByCurrentUser;
    }
}
