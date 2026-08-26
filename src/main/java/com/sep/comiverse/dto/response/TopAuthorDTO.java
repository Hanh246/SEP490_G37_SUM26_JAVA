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
public class TopAuthorDTO {
    private UUID authorId;
    private String fullName;
    private String avatarUrl;
    private long publishedComicsCount;
}
