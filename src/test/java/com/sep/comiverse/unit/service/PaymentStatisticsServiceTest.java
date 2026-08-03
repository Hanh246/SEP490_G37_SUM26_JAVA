package com.sep.comiverse.unit.service;

import com.sep.comiverse.dto.response.PaymentStatisticsResponse;
import com.sep.comiverse.entity.PaymentTransactionEntity;
import com.sep.comiverse.entity.enums.PaymentTransactionStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IPaymentTransactionRepository;
import com.sep.comiverse.repository.IReaderSubscriptionRepository;
import com.sep.comiverse.service.PaymentStatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentStatisticsServiceTest {

    @Mock private IPaymentTransactionRepository paymentRepository;
    @Mock private IReaderSubscriptionRepository subscriptionRepository;

    private PaymentStatisticsService service;

    @BeforeEach
    void setUp() {
        service = new PaymentStatisticsService(paymentRepository, subscriptionRepository);
    }

    @Test
    void statisticsUsePaidAtForRevenueAndCreatedAtForAttemptStatuses() {
        ZoneId zoneId = ZoneId.of("UTC");
        LocalDate today = LocalDate.now(zoneId);
        UUID planId = UUID.randomUUID();
        UUID payingUser = UUID.randomUUID();

        PaymentTransactionEntity paid = transaction(
                PaymentTransactionStatus.PAID,
                "100.00",
                payingUser,
                planId,
                today.atStartOfDay(zoneId).toInstant(),
                today.atTime(1, 0).atZone(zoneId).toInstant()
        );
        PaymentTransactionEntity failed = transaction(
                PaymentTransactionStatus.FAILED,
                "80.00",
                UUID.randomUUID(),
                planId,
                today.atTime(2, 0).atZone(zoneId).toInstant(),
                null
        );
        PaymentTransactionEntity pending = transaction(
                PaymentTransactionStatus.PENDING,
                "70.00",
                UUID.randomUUID(),
                planId,
                today.atTime(3, 0).atZone(zoneId).toInstant(),
                null
        );
        PaymentTransactionEntity refunded = transaction(
                PaymentTransactionStatus.REFUNDED,
                "40.00",
                UUID.randomUUID(),
                planId,
                today.atTime(4, 0).atZone(zoneId).toInstant(),
                today.atTime(4, 30).atZone(zoneId).toInstant()
        );
        LocalDate previousDate = today.minusDays(3);
        PaymentTransactionEntity previousPaid = transaction(
                PaymentTransactionStatus.PAID,
                "50.00",
                UUID.randomUUID(),
                planId,
                previousDate.atStartOfDay(zoneId).toInstant(),
                previousDate.atTime(1, 0).atZone(zoneId).toInstant()
        );

        when(paymentRepository.findDistinctCurrencies()).thenReturn(List.of("USD", "VND"));
        when(paymentRepository.findForStatistics(eq("VND"), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(paid, failed, pending, refunded, previousPaid));
        when(subscriptionRepository.countByDeletedFalseAndStatusInAndCurrentPeriodEndAfter(any(), any()))
                .thenReturn(2L);

        PaymentStatisticsResponse response = service.getStatistics(3, null, "UTC");
        PaymentStatisticsResponse.Summary summary = response.getSummary();

        assertEquals("VND", response.getPeriod().getCurrency());
        assertEquals(today.minusDays(2), response.getPeriod().getFrom());
        assertEquals(today, response.getPeriod().getTo());
        assertEquals(4L, summary.getTotalTransactions());
        assertEquals(1L, summary.getPaidPayments());
        assertEquals(1L, summary.getUniquePayingUsers());
        assertEquals(1L, summary.getPendingPayments());
        assertEquals(1L, summary.getFailedPayments());
        assertEquals(1L, summary.getRefundedPayments());
        assertEquals(new BigDecimal("100.00"), summary.getGrossRevenue());
        assertEquals(new BigDecimal("100.00"), summary.getAverageOrderValue());
        assertEquals(new BigDecimal("66.67"), summary.getSuccessRate());
        assertEquals(new BigDecimal("100.00"), summary.getRevenueChangePercent());
        assertEquals(new BigDecimal("0.00"), summary.getPaidPaymentsChangePercent());
        assertEquals(2L, summary.getActiveSubscriptions());

        assertEquals(3, response.getDailySeries().size());
        PaymentStatisticsResponse.DailyPoint todayPoint = response.getDailySeries().get(2);
        assertEquals(today, todayPoint.getDate());
        assertEquals(new BigDecimal("100.00"), todayPoint.getRevenue());
        assertEquals(1L, todayPoint.getPaidPayments());
        assertEquals(1L, todayPoint.getFailedPayments());

        PaymentStatisticsResponse.StatusBreakdown refundedStatus = response.getStatusBreakdown().stream()
                .filter(item -> item.getStatus() == PaymentTransactionStatus.REFUNDED)
                .findFirst()
                .orElseThrow();
        assertEquals(1L, refundedStatus.getCount());
        assertEquals(new BigDecimal("40.00"), refundedStatus.getAttemptedAmount());

        assertEquals(1, response.getPlanBreakdown().size());
        assertEquals(1L, response.getPlanBreakdown().get(0).getPaidPayments());
        assertEquals(new BigDecimal("100.00"), response.getPlanBreakdown().get(0).getRevenue());
    }

    @Test
    void emptyPreviousPeriodReturnsNullComparisons() {
        when(paymentRepository.findDistinctCurrencies()).thenReturn(List.of());
        when(paymentRepository.findForStatistics(eq("VND"), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());
        when(subscriptionRepository.countByDeletedFalseAndStatusInAndCurrentPeriodEndAfter(any(), any()))
                .thenReturn(0L);

        PaymentStatisticsResponse response = service.getStatistics(30, null, "Asia/Ho_Chi_Minh");

        assertEquals(new BigDecimal("0.00"), response.getSummary().getGrossRevenue());
        assertEquals(new BigDecimal("0.00"), response.getSummary().getSuccessRate());
        assertNull(response.getSummary().getRevenueChangePercent());
        assertNull(response.getSummary().getPaidPaymentsChangePercent());
        assertEquals(30, response.getDailySeries().size());
        assertEquals(List.of("VND"), response.getAvailableCurrencies());
    }

    @Test
    void invalidRangeIsRejectedBeforeQueryingRepositories() {
        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.getStatistics(367, "VND", "UTC")
        );

        assertEquals(400, exception.getCode());
        verify(paymentRepository, never()).findDistinctCurrencies();
    }

    private PaymentTransactionEntity transaction(
            PaymentTransactionStatus status,
            String amount,
            UUID userId,
            UUID planId,
            Instant createdAt,
            Instant paidAt
    ) {
        PaymentTransactionEntity transaction = PaymentTransactionEntity.builder()
                .userId(userId)
                .userEmail("reader@example.com")
                .planId(planId)
                .planCode("MONTHLY")
                .planName("Premium Monthly")
                .amount(new BigDecimal(amount))
                .currency("VND")
                .status(status)
                .paidAt(paidAt)
                .build();
        transaction.setCreatedAt(createdAt);
        return transaction;
    }
}
