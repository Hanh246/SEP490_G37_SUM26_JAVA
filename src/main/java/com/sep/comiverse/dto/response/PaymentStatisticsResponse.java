package com.sep.comiverse.dto.response;

import com.sep.comiverse.entity.enums.PaymentTransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatisticsResponse {
    private Period period;
    private Summary summary;
    private List<DailyPoint> dailySeries;
    private List<StatusBreakdown> statusBreakdown;
    private List<PlanBreakdown> planBreakdown;
    private List<String> availableCurrencies;
    private Instant generatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Period {
        private LocalDate from;
        private LocalDate to;
        private LocalDate previousFrom;
        private LocalDate previousTo;
        private String currency;
        private String zoneId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private Long totalTransactions;
        private Long paidPayments;
        private Long uniquePayingUsers;
        private Long pendingPayments;
        private Long failedPayments;
        private Long expiredPayments;
        private Long refundedPayments;
        private BigDecimal grossRevenue;
        private BigDecimal averageOrderValue;
        private BigDecimal successRate;
        private Long activeSubscriptions;
        private BigDecimal revenueChangePercent;
        private BigDecimal paidPaymentsChangePercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyPoint {
        private LocalDate date;
        private BigDecimal revenue;
        private Long paidPayments;
        private Long failedPayments;
        private Long expiredPayments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusBreakdown {
        private PaymentTransactionStatus status;
        private Long count;
        private BigDecimal attemptedAmount;
        private BigDecimal percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanBreakdown {
        private UUID planId;
        private String planCode;
        private String planName;
        private Long paidPayments;
        private BigDecimal revenue;
        private BigDecimal revenueSharePercent;
    }
}
