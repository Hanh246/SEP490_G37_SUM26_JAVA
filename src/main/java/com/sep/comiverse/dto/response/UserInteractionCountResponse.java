package com.sep.comiverse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInteractionCountResponse {
    private long likedCount;
    private long savedCount;
    private long readCount;
    private long ratingCount;
}
