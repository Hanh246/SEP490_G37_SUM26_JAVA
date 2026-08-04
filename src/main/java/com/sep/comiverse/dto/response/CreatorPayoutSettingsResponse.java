package com.sep.comiverse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorPayoutSettingsResponse {
    private String accountingCurrency;
    private BigDecimal minimumPayoutUsd;
    private BigDecimal translatorTaskRateUsd;
    private BigDecimal translatorMonthlyLimitUsd;
    private Long authorViewsPerUnit;
    private BigDecimal authorViewUnitRateUsd;
    private Long authorFollowsPerUnit;
    private BigDecimal authorFollowUnitRateUsd;
    private BigDecimal authorMonthlyLimitUsd;
    private Instant updatedAt;
    private List<CreatorPayoutCurrencyResponse> supportedCurrencies;
}
