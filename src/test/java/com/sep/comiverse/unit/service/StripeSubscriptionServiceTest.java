package com.sep.comiverse.unit.service;

import com.sep.comiverse.service.StripeGatewayService;
import com.sep.comiverse.service.StripeSubscriptionService;
import com.sep.comiverse.service.SubscriptionPlanService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.entity.PaymentTransactionEntity;
import com.sep.comiverse.entity.ReaderSubscriptionEntity;
import com.sep.comiverse.entity.SubscriptionPlanEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.BillingInterval;
import com.sep.comiverse.entity.enums.PaymentTransactionStatus;
import com.sep.comiverse.entity.enums.ReaderSubscriptionStatus;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
        when(subscriptionRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void failedRenewalCreatesInvoiceTransactionWithoutOverwritingPreviousPayment() throws Exception {
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

}
