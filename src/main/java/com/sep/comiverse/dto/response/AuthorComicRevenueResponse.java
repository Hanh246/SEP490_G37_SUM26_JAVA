package com.sep.comiverse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AuthorComicRevenueResponse {
    private UUID comicId;
    private String comicTitle;
    private Long monthlyViews;
    private Long viewUnits;
    private BigDecimal viewRevenueVnd;
    private Long monthlyFollows;
    private Long followUnits;
    private BigDecimal followRevenueVnd;
    private BigDecimal totalRevenueVnd;
}
