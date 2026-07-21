package com.sep.comiverse.dto;

import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewCommentDTO {
    private UUID id;
    private String bubbleId;
    private UUID authorId;
    private String authorName;
    private String authorInitials;
    private String content;
    private Boolean resolved;
    private Instant createdAt;
}