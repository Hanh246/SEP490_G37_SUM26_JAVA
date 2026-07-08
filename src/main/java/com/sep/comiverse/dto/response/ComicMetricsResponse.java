package com.sep.comiverse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComicMetricsResponse {
    private UUID comicId;
    private UUID authorId;
    private Long viewCount;
    private Long followCount;
    private Long favoriteCount;
    private Long likeCount;
    private BigDecimal estimatedRevenue;
    private Date snapshotAt;
}
