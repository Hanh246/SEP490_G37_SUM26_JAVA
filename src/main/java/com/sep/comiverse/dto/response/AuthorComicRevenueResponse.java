package com.sep.comiverse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorComicRevenueResponse {
    private UUID comicId;
    private String comicTitle;
    private Long monthlyViews;
    private BigDecimal viewUnits;
    private BigDecimal viewRevenueUsd;
    private Long monthlyFollows;
    private BigDecimal followUnits;
    private BigDecimal followRevenueUsd;
    private BigDecimal totalRevenueUsd;
}
