package com.sep.comiverse.service;

import com.sep.comiverse.dto.response.PaymentStatisticsResponse;
import com.sep.comiverse.entity.PaymentTransactionEntity;
import com.sep.comiverse.entity.enums.PaymentTransactionStatus;
import com.sep.comiverse.entity.enums.ReaderSubscriptionStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IPaymentTransactionRepository;
import com.sep.comiverse.repository.IReaderSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PaymentStatisticsService {
    private static final int MAX_RANGE_DAYS = 366;
    private static final String DEFAULT_CURRENCY = "VND";
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("[A-Z]{3}");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final IPaymentTransactionRepository paymentRepository;
    private final IReaderSubscriptionRepository subscriptionRepository;

    @Transactional(readOnly = true)
    public PaymentStatisticsResponse getStatistics(int days, String requestedCurrency, String requestedZoneId) {
        if (days < 1 || days > MAX_RANGE_DAYS) {
            throw badRequest("Payment statistics range must be between 1 and 366 days");
        }

        ZoneId zoneId = parseZoneId(requestedZoneId);
        List<String> availableCurrencies = paymentRepository.findDistinctCurrencies().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
        String currency = resolveCurrency(requestedCurrency, availableCurrencies);

        LocalDate to = LocalDate.now(zoneId);
        LocalDate from = to.minusDays(days - 1L);
        LocalDate previousTo = from.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(days - 1L);

        Instant previousStart = previousFrom.atStartOfDay(zoneId).toInstant();
        Instant currentStart = from.atStartOfDay(zoneId).toInstant();
        Instant currentEnd = to.plusDays(1).atStartOfDay(zoneId).toInstant();

        List<PaymentTransactionEntity> candidates = paymentRepository.findForStatistics(
                currency,
                previousStart,
                currentEnd
        );

        List<PaymentTransactionEntity> currentAttempts = filterByCreatedAt(candidates, currentStart, currentEnd);
        List<PaymentTransactionEntity> currentPaid = filterPaidByPaidAt(candidates, currentStart, currentEnd);
        List<PaymentTransactionEntity> previousPaid = filterPaidByPaidAt(candidates, previousStart, currentStart);

        AttemptAggregate currentAttemptStats = aggregateAttempts(currentAttempts);
        RevenueAggregate currentRevenue = aggregateRevenue(currentPaid);
        RevenueAggregate previousRevenue = aggregateRevenue(previousPaid);
        long activeSubscriptions = subscriptionRepository.countByDeletedFalseAndStatusInAndCurrentPeriodEndAfter(
                List.of(ReaderSubscriptionStatus.ACTIVE, ReaderSubscriptionStatus.TRIALING),
                Instant.now()
        );

        return PaymentStatisticsResponse.builder()
                .period(PaymentStatisticsResponse.Period.builder()
                        .from(from)
                        .to(to)
                        .previousFrom(previousFrom)
                        .previousTo(previousTo)
                        .currency(currency)
                        .zoneId(zoneId.getId())
                        .build())
                .summary(toSummary(
                        currentAttemptStats,
                        currentRevenue,
                        previousRevenue,
                        activeSubscriptions
                ))
                .dailySeries(buildDailySeries(from, to, zoneId, currentAttempts, currentPaid))
                .statusBreakdown(buildStatusBreakdown(currentAttemptStats))
                .planBreakdown(buildPlanBreakdown(currentPaid, currentRevenue.revenue))
                .availableCurrencies(availableCurrencies.isEmpty() ? List.of(currency) : availableCurrencies)
                .generatedAt(Instant.now())
                .build();
    }

    private PaymentStatisticsResponse.Summary toSummary(
            AttemptAggregate currentAttempts,
            RevenueAggregate currentRevenue,
            RevenueAggregate previousRevenue,
            long activeSubscriptions
    ) {
        return PaymentStatisticsResponse.Summary.builder()
                .totalTransactions(currentAttempts.totalTransactions)
                .paidPayments(currentRevenue.paidPayments)
                .uniquePayingUsers((long) currentRevenue.payingUsers.size())
                .pendingPayments(currentAttempts.count(PaymentTransactionStatus.PENDING))
                .failedPayments(currentAttempts.count(PaymentTransactionStatus.FAILED))
                .expiredPayments(currentAttempts.count(PaymentTransactionStatus.EXPIRED))
                .refundedPayments(currentAttempts.count(PaymentTransactionStatus.REFUNDED))
                .grossRevenue(money(currentRevenue.revenue))
                .averageOrderValue(average(currentRevenue.revenue, currentRevenue.paidPayments))
                .successRate(percent(currentAttempts.successfulAttempts, currentAttempts.terminalAttempts))
                .activeSubscriptions(activeSubscriptions)
                .revenueChangePercent(changePercent(currentRevenue.revenue, previousRevenue.revenue))
                .paidPaymentsChangePercent(changePercent(
                        BigDecimal.valueOf(currentRevenue.paidPayments),
                        BigDecimal.valueOf(previousRevenue.paidPayments)
                ))
                .build();
    }

    private List<PaymentStatisticsResponse.DailyPoint> buildDailySeries(
            LocalDate from,
            LocalDate to,
            ZoneId zoneId,
            List<PaymentTransactionEntity> currentAttempts,
            List<PaymentTransactionEntity> currentPaid
    ) {
        Map<LocalDate, DailyAggregate> points = new LinkedHashMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            points.put(date, new DailyAggregate());
        }

        for (PaymentTransactionEntity transaction : currentPaid) {
            LocalDate date = transaction.getPaidAt().atZone(zoneId).toLocalDate();
            DailyAggregate point = points.get(date);
            if (point != null) point.addPaid(safeAmount(transaction));
        }
        for (PaymentTransactionEntity transaction : currentAttempts) {
            if (transaction.getCreatedAt() == null) continue;
            LocalDate date = transaction.getCreatedAt().atZone(zoneId).toLocalDate();
            DailyAggregate point = points.get(date);
            if (point == null) continue;
            if (transaction.getStatus() == PaymentTransactionStatus.FAILED) point.failedPayments++;
            if (transaction.getStatus() == PaymentTransactionStatus.EXPIRED) point.expiredPayments++;
        }

        return points.entrySet().stream()
                .map(entry -> PaymentStatisticsResponse.DailyPoint.builder()
                        .date(entry.getKey())
                        .revenue(money(entry.getValue().revenue))
                        .paidPayments(entry.getValue().paidPayments)
                        .failedPayments(entry.getValue().failedPayments)
                        .expiredPayments(entry.getValue().expiredPayments)
                        .build())
                .toList();
    }

    private List<PaymentStatisticsResponse.StatusBreakdown> buildStatusBreakdown(AttemptAggregate aggregate) {
        return List.of(PaymentTransactionStatus.values()).stream()
                .map(status -> {
                    long count = aggregate.count(status);
                    return PaymentStatisticsResponse.StatusBreakdown.builder()
                            .status(status)
                            .count(count)
                            .attemptedAmount(money(aggregate.amount(status)))
                            .percentage(percent(count, aggregate.totalTransactions))
                            .build();
                })
                .toList();
    }

    private List<PaymentStatisticsResponse.PlanBreakdown> buildPlanBreakdown(
            List<PaymentTransactionEntity> paidTransactions,
            BigDecimal totalRevenue
    ) {
        Map<UUID, PlanAggregate> plans = new LinkedHashMap<>();
        for (PaymentTransactionEntity transaction : paidTransactions) {
            plans.computeIfAbsent(transaction.getPlanId(), ignored -> new PlanAggregate(
                    transaction.getPlanId(),
                    transaction.getPlanCode(),
                    transaction.getPlanName()
            )).add(safeAmount(transaction));
        }

        return plans.values().stream()
                .sorted((left, right) -> right.revenue.compareTo(left.revenue))
                .map(plan -> PaymentStatisticsResponse.PlanBreakdown.builder()
                        .planId(plan.planId)
                        .planCode(plan.planCode)
                        .planName(plan.planName)
                        .paidPayments(plan.paidPayments)
                        .revenue(money(plan.revenue))
                        .revenueSharePercent(sharePercent(plan.revenue, totalRevenue))
                        .build())
                .toList();
    }

    private List<PaymentTransactionEntity> filterByCreatedAt(
            List<PaymentTransactionEntity> transactions,
            Instant from,
            Instant to
    ) {
        return transactions.stream()
                .filter(transaction -> inRange(transaction.getCreatedAt(), from, to))
                .toList();
    }

    private List<PaymentTransactionEntity> filterPaidByPaidAt(
            List<PaymentTransactionEntity> transactions,
            Instant from,
            Instant to
    ) {
        return transactions.stream()
                .filter(transaction -> transaction.getStatus() == PaymentTransactionStatus.PAID)
                .filter(transaction -> inRange(transaction.getPaidAt(), from, to))
                .toList();
    }

    private boolean inRange(Instant value, Instant from, Instant to) {
        return value != null && !value.isBefore(from) && value.isBefore(to);
    }

    private AttemptAggregate aggregateAttempts(List<PaymentTransactionEntity> transactions) {
        AttemptAggregate aggregate = new AttemptAggregate();
        transactions.forEach(aggregate::add);
        return aggregate;
    }

    private RevenueAggregate aggregateRevenue(List<PaymentTransactionEntity> transactions) {
        RevenueAggregate aggregate = new RevenueAggregate();
        transactions.forEach(aggregate::add);
        return aggregate;
    }

    private String resolveCurrency(String requestedCurrency, List<String> availableCurrencies) {
        if (requestedCurrency == null || requestedCurrency.isBlank()) {
            if (availableCurrencies.contains(DEFAULT_CURRENCY)) return DEFAULT_CURRENCY;
            return availableCurrencies.isEmpty() ? DEFAULT_CURRENCY : availableCurrencies.get(0);
        }
        String normalized = requestedCurrency.trim().toUpperCase(Locale.ROOT);
        if (!CURRENCY_PATTERN.matcher(normalized).matches()) {
            throw badRequest("Currency must be a three-letter ISO code");
        }
        return normalized;
    }

    private ZoneId parseZoneId(String requestedZoneId) {
        String value = requestedZoneId == null || requestedZoneId.isBlank()
                ? "Asia/Ho_Chi_Minh"
                : requestedZoneId.trim();
        try {
            return ZoneId.of(value);
        } catch (DateTimeException exception) {
            throw badRequest("Invalid payment statistics time zone");
        }
    }

    private BigDecimal average(BigDecimal amount, long count) {
        if (count == 0) return money(BigDecimal.ZERO);
        return amount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal percent(long numerator, long denominator) {
        if (denominator == 0) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(numerator)
                .multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal sharePercent(BigDecimal amount, BigDecimal total) {
        if (total.signum() == 0) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return amount.multiply(ONE_HUNDRED).divide(total, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal changePercent(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) return null;
        return current.subtract(previous)
                .multiply(ONE_HUNDRED)
                .divide(previous.abs(), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal safeAmount(PaymentTransactionEntity transaction) {
        return transaction.getAmount() == null ? BigDecimal.ZERO : transaction.getAmount();
    }

    private CustomException badRequest(String message) {
        return new CustomException(400, message, HttpStatus.BAD_REQUEST);
    }

    private final class AttemptAggregate {
        private long totalTransactions;
        private long successfulAttempts;
        private long terminalAttempts;
        private final Map<PaymentTransactionStatus, Long> counts = new EnumMap<>(PaymentTransactionStatus.class);
        private final Map<PaymentTransactionStatus, BigDecimal> amounts = new EnumMap<>(PaymentTransactionStatus.class);

        private void add(PaymentTransactionEntity transaction) {
            PaymentTransactionStatus status = transaction.getStatus();
            if (status == null) return;
            totalTransactions++;
            counts.merge(status, 1L, Long::sum);
            amounts.merge(status, safeAmount(transaction), BigDecimal::add);
            if (status != PaymentTransactionStatus.PENDING) terminalAttempts++;
            if (status == PaymentTransactionStatus.PAID || status == PaymentTransactionStatus.REFUNDED) {
                successfulAttempts++;
            }
        }

        private long count(PaymentTransactionStatus status) {
            return counts.getOrDefault(status, 0L);
        }

        private BigDecimal amount(PaymentTransactionStatus status) {
            return amounts.getOrDefault(status, BigDecimal.ZERO);
        }
    }

    private final class RevenueAggregate {
        private long paidPayments;
        private BigDecimal revenue = BigDecimal.ZERO;
        private final Set<UUID> payingUsers = new LinkedHashSet<>();

        private void add(PaymentTransactionEntity transaction) {
            paidPayments++;
            revenue = revenue.add(safeAmount(transaction));
            if (transaction.getUserId() != null) payingUsers.add(transaction.getUserId());
        }
    }

    private static final class DailyAggregate {
        private BigDecimal revenue = BigDecimal.ZERO;
        private long paidPayments;
        private long failedPayments;
        private long expiredPayments;

        private void addPaid(BigDecimal amount) {
            revenue = revenue.add(amount);
            paidPayments++;
        }
    }

    private static final class PlanAggregate {
        private final UUID planId;
        private final String planCode;
        private final String planName;
        private long paidPayments;
        private BigDecimal revenue = BigDecimal.ZERO;

        private PlanAggregate(UUID planId, String planCode, String planName) {
            this.planId = planId;
            this.planCode = planCode;
            this.planName = planName;
        }

        private void add(BigDecimal amount) {
            paidPayments++;
            revenue = revenue.add(amount);
        }
    }
}
