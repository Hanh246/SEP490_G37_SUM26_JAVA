package com.sep.comiverse.unit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.dto.request.UpdatePremiumPlanSettingsRequest;
import com.sep.comiverse.dto.response.PremiumPlanSettingsResponse;
import com.sep.comiverse.dto.response.UpgradePlanResponse;
import com.sep.comiverse.entity.ReaderSubscriptionEntity;
import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.SubscriptionPlanEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.BillingInterval;
import com.sep.comiverse.entity.enums.ReaderSubscriptionStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IReaderSubscriptionRepository;
import com.sep.comiverse.repository.ISubscriptionPlanRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.service.PremiumPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PremiumPlanServiceTest {

    @Mock
    private ISubscriptionPlanRepository subscriptionPlanRepository;
    @Mock
    private IReaderSubscriptionRepository readerSubscriptionRepository;
    @Mock
    private IUserRepository userRepository;

    private PremiumPlanService service;

    @BeforeEach
    void setUp() {
        service = new PremiumPlanService(
                subscriptionPlanRepository,
                readerSubscriptionRepository,
                userRepository,
                new ObjectMapper()
        );
    }

    @Test
    void getSettingsReturnsSafeDefaultsWhenCanonicalPlansAreMissing() {
        when(subscriptionPlanRepository.findByCodeIgnoreCaseAndDeletedFalse("MONTHLY"))
                .thenReturn(Optional.empty());
        when(subscriptionPlanRepository.findByCodeIgnoreCaseAndDeletedFalse("YEARLY"))
                .thenReturn(Optional.empty());

        PremiumPlanSettingsResponse response = service.getPremiumPlanSettings();

        assertEquals(0, response.getMonthlyPrice().compareTo(new BigDecimal("3.16")));
        assertEquals(0, response.getYearlyPrice().compareTo(new BigDecimal("31.60")));
        assertEquals(5, response.getBenefits().size());
        assertTrue(response.getBenefits().contains("Offline chapter downloads"));
    }

    @Test
    void getSettingsFallsBackToDefaultBenefitsWhenStoredJsonIsInvalid() {
        SubscriptionPlanEntity monthly = plan("MONTHLY", new BigDecimal("3.16"), BillingInterval.MONTH);
        monthly.setFeaturesJson("not-json");
        when(subscriptionPlanRepository.findByCodeIgnoreCaseAndDeletedFalse("MONTHLY"))
                .thenReturn(Optional.of(monthly));
        when(subscriptionPlanRepository.findByCodeIgnoreCaseAndDeletedFalse("YEARLY"))
                .thenReturn(Optional.empty());

        PremiumPlanSettingsResponse response = service.getPremiumPlanSettings();

        assertTrue(response.getBenefits().contains("Read without ads"));
        assertEquals(5, response.getBenefits().size());
    }

    @Test
    void updateSettingsSanitizesBenefitsAndInvalidatesChangedStripePrices() {
        SubscriptionPlanEntity monthly = plan("MONTHLY", new BigDecimal("3.16"), BillingInterval.MONTH);
        SubscriptionPlanEntity yearly = plan("YEARLY", new BigDecimal("31.60"), BillingInterval.YEAR);
        when(subscriptionPlanRepository.findByCodeIgnoreCaseAndDeletedFalse("MONTHLY"))
                .thenReturn(Optional.of(monthly));
        when(subscriptionPlanRepository.findByCodeIgnoreCaseAndDeletedFalse("YEARLY"))
                .thenReturn(Optional.of(yearly));
        UpdatePremiumPlanSettingsRequest request = UpdatePremiumPlanSettingsRequest.builder()
                .monthlyPrice(new BigDecimal("3.56"))
                .yearlyPrice(new BigDecimal("35.60"))
                .benefits(List.of("  No ads  ", "Offline reading", "No ads", "  "))
                .build();

        PremiumPlanSettingsResponse response = service.updatePremiumPlanSettings(request);

        assertEquals(List.of("No ads", "Offline reading"), response.getBenefits());
        assertEquals(0, monthly.getPrice().compareTo(new BigDecimal("3.56")));
        assertEquals(0, yearly.getPrice().compareTo(new BigDecimal("35.60")));
        assertNull(monthly.getStripePriceId());
        assertNull(yearly.getStripePriceId());
        assertEquals("USD", monthly.getCurrency());
        assertEquals(BillingInterval.MONTH, monthly.getBillingInterval());
        assertEquals(BillingInterval.YEAR, yearly.getBillingInterval());
        verify(subscriptionPlanRepository).save(monthly);
        verify(subscriptionPlanRepository).save(yearly);
    }

    @Test
    void updateSettingsKeepsStripePriceWhenBillingDefinitionDidNotChange() {
        SubscriptionPlanEntity monthly = plan("MONTHLY", new BigDecimal("3.16"), BillingInterval.MONTH);
        SubscriptionPlanEntity yearly = plan("YEARLY", new BigDecimal("31.60"), BillingInterval.YEAR);
        when(subscriptionPlanRepository.findByCodeIgnoreCaseAndDeletedFalse("MONTHLY"))
                .thenReturn(Optional.of(monthly));
        when(subscriptionPlanRepository.findByCodeIgnoreCaseAndDeletedFalse("YEARLY"))
                .thenReturn(Optional.of(yearly));
        UpdatePremiumPlanSettingsRequest request = UpdatePremiumPlanSettingsRequest.builder()
                .monthlyPrice(new BigDecimal("3.16"))
                .yearlyPrice(new BigDecimal("31.60"))
                .benefits(List.of("No ads"))
                .build();

        service.updatePremiumPlanSettings(request);

        assertEquals("price_MONTHLY", monthly.getStripePriceId());
        assertEquals("price_YEARLY", yearly.getStripePriceId());
    }

    @Test
    void updateSettingsRejectsEmptySanitizedBenefits() {
        UpdatePremiumPlanSettingsRequest request = UpdatePremiumPlanSettingsRequest.builder()
                .monthlyPrice(new BigDecimal("3.16"))
                .yearlyPrice(new BigDecimal("31.60"))
                .benefits(List.of(" ", "  "))
                .build();

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.updatePremiumPlanSettings(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
        assertEquals("At least one premium benefit is required", error.getMessage());
        verifyNoInteractions(subscriptionPlanRepository);
    }

    @Test
    void updateSettingsFailsClearlyWhenCanonicalPlanIsMissing() {
        when(subscriptionPlanRepository.findByCodeIgnoreCaseAndDeletedFalse("MONTHLY"))
                .thenReturn(Optional.empty());
        UpdatePremiumPlanSettingsRequest request = UpdatePremiumPlanSettingsRequest.builder()
                .monthlyPrice(new BigDecimal("3.16"))
                .yearlyPrice(new BigDecimal("31.60"))
                .benefits(List.of("No ads"))
                .build();

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.updatePremiumPlanSettings(request)
        );

        assertEquals(HttpStatus.CONFLICT, error.getHttpStatus());
        assertEquals("Canonical MONTHLY subscription plan is missing", error.getMessage());
        verify(subscriptionPlanRepository, never()).save(any());
    }

    @Test
    void upgradePlanRejectsUnknownPlanBeforeLoadingUser() {
        CustomException error = assertThrows(
                CustomException.class,
                () -> service.upgradePlan(UUID.randomUUID(), "weekly")
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
        assertEquals("Invalid plan type", error.getMessage());
        verifyNoInteractions(userRepository);
    }

    @Test
    void upgradePlanExtendsAnExistingPremiumPeriod() {
        UUID userId = UUID.randomUUID();
        LocalDateTime currentExpiry = LocalDateTime.now(ZoneOffset.UTC).plusDays(10);
        UserEntity user = user(userId);
        user.setPremiumPlan("MONTHLY");
        user.setPremiumExpiresAt(currentExpiry);
        when(userRepository.findByIdWithRole(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(readerSubscriptionRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.empty());

        UpgradePlanResponse response = service.upgradePlan(userId, " yearly ");

        assertEquals("YEARLY", user.getPremiumPlan());
        assertEquals(currentExpiry.plusYears(1), user.getPremiumExpiresAt());
        assertEquals(currentExpiry.plusYears(1), response.getPremiumExpiresAt());
        assertTrue(response.getPremiumActive());
        assertEquals("YEARLY", response.getUser().getPremiumPlan());
        verify(userRepository).save(user);
    }

    @Test
    void activeSubscriptionIsAuthoritativeForPremiumAccess() {
        UUID userId = UUID.randomUUID();
        UserEntity user = user(userId);
        user.setPremiumPlan("YEARLY");
        user.setPremiumExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
        ReaderSubscriptionEntity subscription = ReaderSubscriptionEntity.builder()
                .userId(userId)
                .planId(UUID.randomUUID())
                .planCode("MONTHLY")
                .planName("Premium Monthly")
                .status(ReaderSubscriptionStatus.ACTIVE)
                .currentPeriodEnd(Instant.now().plusSeconds(3600))
                .build();
        when(readerSubscriptionRepository.findByUserIdAndDeletedFalse(userId))
                .thenReturn(Optional.of(subscription));

        assertTrue(service.hasActivePremium(user));
        assertEquals("MONTHLY", service.toUserProfileResponse(user).getPremiumPlan());
    }

    @Test
    void inactiveSubscriptionOverridesLegacyPremiumCache() {
        UUID userId = UUID.randomUUID();
        UserEntity user = user(userId);
        user.setPremiumPlan("YEARLY");
        user.setPremiumExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusYears(1));
        ReaderSubscriptionEntity subscription = ReaderSubscriptionEntity.builder()
                .userId(userId)
                .planId(UUID.randomUUID())
                .planCode("MONTHLY")
                .planName("Premium Monthly")
                .status(ReaderSubscriptionStatus.CANCELED)
                .currentPeriodEnd(Instant.now().plusSeconds(3600))
                .build();
        when(readerSubscriptionRepository.findByUserIdAndDeletedFalse(userId))
                .thenReturn(Optional.of(subscription));

        assertFalse(service.hasActivePremium(user));
        assertNull(service.toUserProfileResponse(user).getPremiumPlan());
    }

    private SubscriptionPlanEntity plan(String code, BigDecimal price, BillingInterval interval) {
        return SubscriptionPlanEntity.builder()
                .code(code)
                .name("Premium " + code)
                .price(price)
                .currency("USD")
                .billingInterval(interval)
                .intervalCount(1)
                .featuresJson("[\"No ads\"]")
                .stripePriceId("price_" + code)
                .build();
    }

    private UserEntity user(UUID userId) {
        UserEntity user = UserEntity.builder()
                .username("reader")
                .fullName("Reader One")
                .email("reader@example.com")
                .role(RoleEntity.builder().roleName("READER").build())
                .status("ACTIVE")
                .build();
        user.setId(userId);
        return user;
    }
}
