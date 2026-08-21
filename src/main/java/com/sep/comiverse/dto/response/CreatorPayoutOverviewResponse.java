package com.sep.comiverse.dto.response;

import com.sep.comiverse.entity.enums.CreatorPayoutRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorPayoutOverviewResponse {
    private CreatorPayoutRole role;
    private String selectedMonth;
    private String lastClosedMonth;
    private String latestRequestableMonth;
    private Boolean currentMonthAllowed;

    private BigDecimal monthlyGrossAmountUsd;
    private BigDecimal monthlyWithdrawableAmountUsd;
    private BigDecimal monthlyOverLimitAmountUsd;
    private BigDecimal monthlyLimitAmountUsd;
    private BigDecimal minimumPayoutAmountUsd;
    private BigDecimal lifetimePaidAmountUsd;
    private BigDecimal pendingAmountUsd;
    private BigDecimal availableBalanceAmountUsd;
    private BigDecimal cumulativeEarnedAmountUsd;
    private BigDecimal pendingCurrentMonthAmountUsd;

    private BigDecimal monthlyGrossAmount;
    private BigDecimal monthlyWithdrawableAmount;
    private BigDecimal monthlyOverLimitAmount;
    private BigDecimal monthlyLimitAmount;
    private BigDecimal minimumPayoutAmount;
    private BigDecimal lifetimePaidAmount;
    private BigDecimal pendingAmount;
    private BigDecimal availableBalanceAmount;
    private BigDecimal cumulativeEarnedAmount;
    private BigDecimal pendingCurrentMonthAmount;

    private String payoutCurrency;
    private String payoutCurrencySymbol;
    private BigDecimal payoutUnitsPerUsd;
    private String accountCountry;
    private List<CreatorPayoutCurrencyResponse> supportedCurrencies;

    private Boolean requestable;
    private String notRequestableReason;
    private BigDecimal calculationUnitCount;
    private String calculationUnitLabel;
    private BigDecimal calculationUnitRateUsd;
    private BigDecimal calculationUnitRate;

    /** Legacy alias retained for frontend compatibility; now represents the default rate per page. */
    private BigDecimal translatorTaskRateUsd;
    private BigDecimal translatorPageRateUsd;
    private Long authorViewsPerUnit;
    private BigDecimal authorViewUnitRateUsd;
    private Long authorFollowsPerUnit;
    private BigDecimal authorFollowUnitRateUsd;
    private String calculationPolicy;

    private List<TranslatorTaskRevenueResponse> translatorTasks;
    private List<AuthorComicRevenueResponse> authorComics;
    private CreatorPayoutAccountResponse account;
    private CreatorPayoutRequestResponse existingRequest;
    private List<CreatorPayoutRequestResponse> requests;
}
