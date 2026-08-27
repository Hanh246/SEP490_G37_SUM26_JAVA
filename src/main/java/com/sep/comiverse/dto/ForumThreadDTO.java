package com.sep.comiverse.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForumThreadDTO {
    private UUID id;
    private String title;
    private String author;
    private UUID authorId;
    private String avatarUrl;
    private String category;
    private String content;
    private Boolean isPinned;
    private Boolean isLocked;
    private Boolean isReported;
    private String reportReason;
    private Instant createdAt;
    private Integer replies;
    private Integer views;
    private Integer likes;
    private Boolean isLikedByCurrentUser;
    private Boolean isFollowedByCurrentUser;
}
