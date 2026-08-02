package com.sep.comiverse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CreatorPayoutSettingsResponse {
    private BigDecimal minimumPayoutVnd;
    private BigDecimal translatorTaskRateVnd;
    private BigDecimal translatorMonthlyLimitVnd;
    private Long authorViewsPerUnit;
    private BigDecimal authorViewUnitRateVnd;
    private Long authorFollowsPerUnit;
    private BigDecimal authorFollowUnitRateVnd;
    private BigDecimal authorMonthlyLimitVnd;
    private Instant updatedAt;
    private List<CreatorCurrencyRateResponse> currencyRates;
}
