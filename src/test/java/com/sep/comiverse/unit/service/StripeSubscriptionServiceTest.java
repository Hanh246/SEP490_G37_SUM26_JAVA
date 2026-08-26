package com.sep.comiverse.unit.service;

import com.sep.comiverse.service.StripeGatewayService;
import com.sep.comiverse.service.StripeSubscriptionService;
import com.sep.comiverse.service.SubscriptionPlanService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.entity.PaymentTransactionEntity;
import com.sep.comiverse.entity.ReaderSubscriptionEntity;
import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.SubscriptionPlanEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.BillingInterval;
import com.sep.comiverse.entity.enums.PaymentTransactionStatus;
import com.sep.comiverse.entity.enums.ReaderSubscriptionStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IPaymentTransactionRepository;
import com.sep.comiverse.repository.IReaderSubscriptionRepository;
import com.sep.comiverse.repository.IStripeWebhookEventRepository;
import com.sep.comiverse.repository.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeSubscriptionServiceTest {

    @Mock private SubscriptionPlanService planService;
    @Mock private StripeGatewayService gateway;
    @Mock private IPaymentTransactionRepository paymentRepository;
    @Mock private IReaderSubscriptionRepository subscriptionRepository;
    @Mock private IStripeWebhookEventRepository webhookEventRepository;
    @Mock private IUserRepository userRepository;

    private StripeSubscriptionService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new StripeSubscriptionService(
                planService,
                gateway,
                paymentRepository,
                subscriptionRepository,
                webhookEventRepository,
                userRepository
        );
    }

    @Test
    void failedRenewalCreatesInvoiceTransactionWithoutOverwritingPreviousPayment() throws Exception {
        when(subscriptionRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        String subscriptionId = "sub_123";
        PaymentTransactionEntity previousPaid = PaymentTransactionEntity.builder()
                .status(PaymentTransactionStatus.PAID)
                .stripeInvoiceId("in_previous")
                .build();
        SubscriptionPlanEntity plan = SubscriptionPlanEntity.builder()
                .code("MONTHLY")
                .name("Premium Monthly")
                .price(new BigDecimal("79000"))
                .currency("VND")
                .billingInterval(BillingInterval.MONTH)
                .intervalCount(1)
                .build();
        plan.setId(planId);
        UserEntity user = UserEntity.builder().email("reader@example.com").build();
        ReaderSubscriptionEntity subscription = ReaderSubscriptionEntity.builder()
                .userId(userId)
                .status(ReaderSubscriptionStatus.ACTIVE)
                .stripeSubscriptionId(subscriptionId)
                .build();
        JsonNode stripeSubscription = objectMapper.readTree("""
                {
                  "id":"sub_123",
                  "metadata":{"user_id":"%s","plan_id":"%s"}
                }
                """.formatted(userId, planId));
        JsonNode event = objectMapper.readTree("""
                {
                  "id":"evt_failed_renewal",
                  "type":"invoice.payment_failed",
                  "data":{"object":{
                    "id":"in_failed",
                    "subscription":"sub_123",
                    "customer":"cus_123",
                    "currency":"vnd",
                    "amount_due":79000
                  }}
                }
                """);

        when(webhookEventRepository.existsByEventIdAndDeletedFalse("evt_failed_renewal")).thenReturn(false);
        when(gateway.retrieveSubscription(subscriptionId)).thenReturn(stripeSubscription);
        when(paymentRepository.findByStripeInvoiceIdAndDeletedFalse("in_failed")).thenReturn(Optional.empty());
        when(planService.getPlanEntity(planId)).thenReturn(plan);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionRepository.findByStripeSubscriptionIdAndDeletedFalse(subscriptionId))
                .thenReturn(Optional.of(subscription));

        service.processWebhook(event);

        ArgumentCaptor<PaymentTransactionEntity> captor = ArgumentCaptor.forClass(PaymentTransactionEntity.class);
        verify(paymentRepository).save(captor.capture());
        PaymentTransactionEntity failed = captor.getValue();
        assertNotSame(previousPaid, failed);
        assertEquals(PaymentTransactionStatus.FAILED, failed.getStatus());
        assertEquals("in_failed", failed.getStripeInvoiceId());
        verify(paymentRepository, never())
                .findFirstByStripeSubscriptionIdAndDeletedFalseOrderByCreatedAtDesc(subscriptionId);
        assertEquals(PaymentTransactionStatus.PAID, previousPaid.getStatus());
    }
    @Test
    void paidCheckoutStatusReconcilesSubscriptionBeforeReportingPremiumActive() throws Exception {
        when(subscriptionRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        String sessionId = "cs_test_paid";
        String subscriptionId = "sub_paid";
        PaymentTransactionEntity transaction = PaymentTransactionEntity.builder()
                .userId(userId)
                .planId(planId)
                .planCode("MONTHLY")
                .planName("Premium Monthly")
                .status(PaymentTransactionStatus.PAID)
                .stripeCheckoutSessionId(sessionId)
                .build();
        SubscriptionPlanEntity plan = SubscriptionPlanEntity.builder()
                .code("MONTHLY")
                .name("Premium Monthly")
                .price(new BigDecimal("79000"))
                .currency("VND")
                .billingInterval(BillingInterval.MONTH)
                .intervalCount(1)
                .build();
        plan.setId(planId);
        UserEntity user = UserEntity.builder().email("reader@example.com").build();
        user.setId(userId);
        long periodEnd = Instant.now().plusSeconds(30L * 24 * 60 * 60).getEpochSecond();
        JsonNode checkoutSession = objectMapper.readTree("""
                {
                  "id":"cs_test_paid",
                  "subscription":"sub_paid",
                  "customer":"cus_paid"
                }
                """);
        JsonNode stripeSubscription = objectMapper.readTree("""
                {
                  "id":"sub_paid",
                  "customer":"cus_paid",
                  "status":"active",
                  "current_period_start":%d,
                  "current_period_end":%d
                }
                """.formatted(Instant.now().getEpochSecond(), periodEnd));

        when(paymentRepository.findByStripeCheckoutSessionIdAndDeletedFalse(sessionId))
                .thenReturn(Optional.of(transaction));
        when(subscriptionRepository.findByUserIdAndDeletedFalse(userId))
                .thenReturn(Optional.empty());
        when(gateway.retrieveCheckoutSession(sessionId)).thenReturn(checkoutSession);
        when(gateway.retrieveSubscription(subscriptionId)).thenReturn(stripeSubscription);
        when(planService.getPlanEntity(planId)).thenReturn(plan);
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        var response = service.getCheckoutStatus(userId, sessionId);

        assertEquals(PaymentTransactionStatus.PAID, response.getPaymentStatus());
        assertTrue(response.getPremiumActive());
        assertEquals("MONTHLY", user.getPremiumPlan());
        assertEquals(subscriptionId, transaction.getStripeSubscriptionId());
    }

    @Test
    void paymentHistoryIsLoadedOnlyForTheAuthenticatedReader() {
        UUID userId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-08-20T10:15:30Z");
        PaymentTransactionEntity payment = PaymentTransactionEntity.builder()
                .userId(userId)
                .userEmail("reader@example.com")
                .planId(UUID.randomUUID())
                .planCode("MONTHLY")
                .planName("Premium Monthly")
                .amount(new BigDecimal("79000"))
                .currency("VND")
                .status(PaymentTransactionStatus.PAID)
                .provider("STRIPE")
                .stripeCheckoutSessionId("cs_private")
                .build();
        payment.setId(paymentId);
        payment.setCreatedAt(createdAt);
        when(paymentRepository.findAllByUserIdAndDeletedFalseOrderByCreatedAtDesc(
                eq(userId), any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(payment)));

        var history = service.getPaymentHistory(userId, 0, 20);

        assertEquals(1, history.getContent().size());
        assertEquals(paymentId, history.getContent().getFirst().getId());
        assertEquals("MONTHLY", history.getContent().getFirst().getPlanCode());
        assertEquals(PaymentTransactionStatus.PAID, history.getContent().getFirst().getStatus());
        assertEquals(createdAt, history.getContent().getFirst().getCreatedAt());
        verify(paymentRepository).findAllByUserIdAndDeletedFalseOrderByCreatedAtDesc(
                eq(userId), any(Pageable.class)
        );
    }

    @Test
    void paymentHistoryReconcilesAnAbandonedCheckoutAsExpired() throws Exception {
        UUID userId = UUID.randomUUID();
        String sessionId = "cs_expired_history";
        PaymentTransactionEntity payment = PaymentTransactionEntity.builder()
                .userId(userId)
                .userEmail("reader@example.com")
                .planId(UUID.randomUUID())
                .planCode("MONTHLY")
                .planName("Premium Monthly")
                .amount(new BigDecimal("79000"))
                .currency("VND")
                .status(PaymentTransactionStatus.PENDING)
                .provider("STRIPE")
                .stripeCheckoutSessionId(sessionId)
                .build();
        payment.setId(UUID.randomUUID());
        payment.setCreatedAt(Instant.parse("2026-08-20T10:15:30Z"));
        when(paymentRepository.findAllByUserIdAndDeletedFalseOrderByCreatedAtDesc(
                eq(userId), any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(payment)));
        when(paymentRepository.findByStripeCheckoutSessionIdAndDeletedFalse(sessionId))
                .thenReturn(Optional.of(payment));
        when(gateway.retrieveCheckoutSession(sessionId)).thenReturn(objectMapper.readTree("""
                {
                  "id": "cs_expired_history",
                  "status": "expired",
                  "payment_status": "unpaid"
                }
                """));
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var history = service.getPaymentHistory(userId, 0, 20);

        assertEquals(PaymentTransactionStatus.EXPIRED, history.getContent().getFirst().getStatus());
        assertEquals(
                "Stripe Checkout session expired before payment was completed",
                history.getContent().getFirst().getFailureReason()
        );
    }

    @Test
    void legacyActivePremiumAccountCannotStartAnotherCheckout() {
        UUID userId = UUID.randomUUID();
        UserEntity user = UserEntity.builder()
                .email("legacy@example.com")
                .role(RoleEntity.builder().roleName("READER").build())
                .premiumPlan("MONTHLY")
                .premiumExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(10))
                .build();
        user.setId(userId);
        when(userRepository.findByIdWithRole(userId)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.createCheckoutSession(userId, UUID.randomUUID())
        );

        assertEquals(409, exception.getCode());
        verify(gateway, never()).createCheckoutSession(any(), any(), any(), any());
    }

}
