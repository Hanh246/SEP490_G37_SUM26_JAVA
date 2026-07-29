package com.sep.comiverse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.dto.request.SubscriptionPlanRequest;
import com.sep.comiverse.entity.SubscriptionPlanEntity;
import com.sep.comiverse.entity.enums.BillingInterval;
import com.sep.comiverse.repository.ISubscriptionPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionPlanServiceTest {

    @Mock
    private ISubscriptionPlanRepository planRepository;
    @Mock
    private StripeGatewayService stripeGatewayService;

    private SubscriptionPlanService service;
    private UUID planId;

    @BeforeEach
    void setUp() {
        service = new SubscriptionPlanService(planRepository, stripeGatewayService, new ObjectMapper());
        planId = UUID.randomUUID();
        when(planRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void nameChangeUpdatesExistingProductWithoutReplacingPrice() {
        SubscriptionPlanEntity plan = existingPlan();
        when(planRepository.findByIdAndDeletedFalse(planId)).thenReturn(Optional.of(plan));

        SubscriptionPlanRequest request = request("Premium Monthly Plus", new BigDecimal("79000"));
        service.updatePlan(planId, request);

        verify(stripeGatewayService).updateProduct(plan);
        assertEquals("prod_123", plan.getStripeProductId());
        assertEquals("price_123", plan.getStripePriceId());
    }

    @Test
    void priceChangeKeepsProductAndInvalidatesOnlyPrice() {
        SubscriptionPlanEntity plan = existingPlan();
        when(planRepository.findByIdAndDeletedFalse(planId)).thenReturn(Optional.of(plan));

        SubscriptionPlanRequest request = request("Premium Monthly", new BigDecimal("89000"));
        service.updatePlan(planId, request);

        verify(stripeGatewayService, never()).updateProduct(plan);
        assertEquals("prod_123", plan.getStripeProductId());
        assertNull(plan.getStripePriceId());
    }

    private SubscriptionPlanEntity existingPlan() {
        SubscriptionPlanEntity plan = SubscriptionPlanEntity.builder()
                .code("MONTHLY")
                .name("Premium Monthly")
                .description("Reader subscription")
                .price(new BigDecimal("79000"))
                .currency("VND")
                .billingInterval(BillingInterval.MONTH)
                .intervalCount(1)
                .active(true)
                .recommended(true)
                .featuresJson("[]")
                .sortOrder(10)
                .stripeProductId("prod_123")
                .stripePriceId("price_123")
                .build();
        plan.setId(planId);
        return plan;
    }

    private SubscriptionPlanRequest request(String name, BigDecimal price) {
        return SubscriptionPlanRequest.builder()
                .code("MONTHLY")
                .name(name)
                .description("Reader subscription")
                .price(price)
                .currency("VND")
                .billingInterval(BillingInterval.MONTH)
                .intervalCount(1)
                .active(true)
                .recommended(true)
                .features(List.of())
                .sortOrder(10)
                .build();
    }
}
