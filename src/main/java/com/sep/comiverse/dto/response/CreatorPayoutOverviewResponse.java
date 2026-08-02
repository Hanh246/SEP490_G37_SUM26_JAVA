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
    private BigDecimal monthlyGrossAmount;
    private BigDecimal monthlyGrossAmountVnd;
    private BigDecimal monthlyWithdrawableAmount;
    private BigDecimal monthlyWithdrawableAmountVnd;
    private BigDecimal monthlyOverLimitAmount;
    private BigDecimal monthlyOverLimitAmountVnd;
    private BigDecimal monthlyLimitAmount;
    private BigDecimal monthlyLimitAmountVnd;
    private BigDecimal minimumPayoutAmount;
    private BigDecimal minimumPayoutAmountVnd;
    private String payoutCurrency;
    private String accountCountry;
    private BigDecimal exchangeRateVndPerUnit;
    private BigDecimal lifetimePaidAmount;
    private BigDecimal pendingAmount;
    private Boolean requestable;
    private String notRequestableReason;
    private Long calculationUnitCount;
    private String calculationUnitLabel;
    private BigDecimal calculationUnitRate;
    private BigDecimal translatorTaskRateVnd;
    private Long authorViewsPerUnit;
    private BigDecimal authorViewUnitRateVnd;
    private Long authorFollowsPerUnit;
    private BigDecimal authorFollowUnitRateVnd;
    private String calculationPolicy;
    private List<TranslatorTaskRevenueResponse> translatorTasks;
    private List<AuthorComicRevenueResponse> authorComics;
    private CreatorPayoutAccountResponse account;
    private CreatorPayoutRequestResponse existingRequest;
    private List<CreatorPayoutRequestResponse> requests;
}