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
    private BigDecimal monthlyGrossAmount;
    private BigDecimal minimumPayoutAmount;
    private BigDecimal lifetimePaidAmount;
    private BigDecimal pendingAmount;
    private Boolean requestable;
    private String notRequestableReason;
    private Long calculationUnitCount;
    private String calculationUnitLabel;
    private BigDecimal calculationUnitRate;
    private CreatorPayoutAccountResponse account;
    private CreatorPayoutRequestResponse existingRequest;
    private List<CreatorPayoutRequestResponse> requests;
}
