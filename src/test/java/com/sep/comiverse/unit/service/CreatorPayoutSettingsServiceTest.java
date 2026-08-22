package com.sep.comiverse.unit.service;

import com.sep.comiverse.dto.request.UpsertCreatorPayoutCurrencyRequest;
import com.sep.comiverse.entity.CreatorPayoutCurrencyEntity;
import com.sep.comiverse.entity.CreatorPayoutSettingEntity;
import com.sep.comiverse.entity.enums.CreatorPayoutCurrency;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.ICreatorPayoutCurrencyRepository;
import com.sep.comiverse.repository.ICreatorPayoutSettingRepository;
import com.sep.comiverse.service.CreatorPayoutSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatorPayoutSettingsServiceTest {

    @Mock private ICreatorPayoutSettingRepository settingRepository;
    @Mock private ICreatorPayoutCurrencyRepository currencyRepository;
    @Mock private JdbcTemplate jdbcTemplate;
    private CreatorPayoutSettingsService service;

    @BeforeEach
    void setUp() {
        service = new CreatorPayoutSettingsService(settingRepository, currencyRepository, jdbcTemplate);
        lenient().when(currencyRepository.save(any(CreatorPayoutCurrencyEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void currentSettings_missingRow_returnsDetachedUsdDefaults() {
        when(settingRepository.findByConfigKeyAndDeletedFalse("DEFAULT")).thenReturn(Optional.empty());

        CreatorPayoutSettingEntity result = service.currentSettings();

        assertEquals("DEFAULT", result.getConfigKey());
        assertEquals("USD", result.getCurrency());
        assertEquals(new BigDecimal("10.00"), result.getMinimumPayoutUsd());
    }

    @Test
    void normalizeMoney_andRate_coverNullRoundingAndInvalidBoundary() {
        assertEquals(new BigDecimal("0.00"), service.normalizeMoney(null));
        assertEquals(new BigDecimal("10.24"), service.normalizeMoney(new BigDecimal("10.235")));
        assertEquals(new BigDecimal("1.234568"), service.normalizeRate(new BigDecimal("1.2345678")));
        assertEquals(400, assertThrows(CustomException.class, () -> service.normalizeRate(null)).getCode());
        assertEquals(400, assertThrows(CustomException.class, () -> service.normalizeRate(BigDecimal.ZERO)).getCode());
        assertEquals(400, assertThrows(CustomException.class, () -> service.normalizeRate(new BigDecimal("-1"))).getCode());
    }

    @Test
    void conversion_roundTripsUsingResolvedUsdRate() {
        var usd = new CreatorPayoutSettingsService.ResolvedCurrency(
                CreatorPayoutCurrency.USD, new BigDecimal("1.000000"));

        BigDecimal converted = service.convertUsdToCurrency(new BigDecimal("100.00"), usd);
        BigDecimal roundTrip = service.convertCurrencyToUsd(converted, usd);

        assertEquals(new BigDecimal("100.00"), converted);
        assertEquals(new BigDecimal("100.00"), roundTrip);
    }

    @Test
    void convertCurrencyToUsd_rejectsBrokenRate() {
        var broken = new CreatorPayoutSettingsService.ResolvedCurrency(
                CreatorPayoutCurrency.USD, BigDecimal.ZERO);
        assertEquals(500, assertThrows(CustomException.class,
                () -> service.convertCurrencyToUsd(BigDecimal.TEN, broken)).getCode());
    }

    @Test
    void upsertCurrency_usdForcesOneToOneRateAndActive() {
        UpsertCreatorPayoutCurrencyRequest request = new UpsertCreatorPayoutCurrencyRequest();
        request.setCurrencyCode("usd");
        request.setUnitsPerUsd(new BigDecimal("123.456"));
        request.setActive(false);
        when(currencyRepository.findByCurrencyCode("USD")).thenReturn(Optional.empty());

        var response = service.upsertCurrency(request);

        assertEquals("USD", response.getCurrencyCode());
        assertEquals(new BigDecimal("1.000000"), response.getUnitsPerUsd());
        assertTrue(response.getActive());
    }

    @Test
    void upsertCurrency_rejectsUnsupportedCode() {
        UpsertCreatorPayoutCurrencyRequest invalidCode = new UpsertCreatorPayoutCurrencyRequest();
        invalidCode.setCurrencyCode("BTC");
        invalidCode.setUnitsPerUsd(BigDecimal.ONE);
        invalidCode.setActive(true);

        assertEquals(400, assertThrows(CustomException.class,
                () -> service.upsertCurrency(invalidCode)).getCode());
    }

    @Test
    void resolveCurrency_returnsActiveUsdRate() {
        CreatorPayoutCurrencyEntity usd = CreatorPayoutCurrencyEntity.builder()
                .currencyCode("USD")
                .displayName("US Dollar")
                .symbol("$")
                .unitsPerUsd(new BigDecimal("1.000000"))
                .active(true)
                .build();
        when(currencyRepository.findByCurrencyCode("USD")).thenReturn(Optional.of(usd));
        when(currencyRepository.findByCurrencyCodeAndDeletedFalse("USD")).thenReturn(Optional.of(usd));

        var resolved = service.resolveCurrency("usd");

        assertEquals("USD", resolved.code());
        assertEquals(new BigDecimal("1.000000"), resolved.unitsPerUsd());
    }

    @Test
    void resolveCurrency_rejectsUnsupportedCurrency() {
        assertEquals(400, assertThrows(CustomException.class,
                () -> service.resolveCurrency("EUR")).getCode());
    }

    @Test
    void resolveCurrency_rejectsMissingUsdConfiguration() {
        when(currencyRepository.findByCurrencyCode("USD")).thenReturn(Optional.empty());
        when(currencyRepository.findByCurrencyCodeAndDeletedFalse("USD")).thenReturn(Optional.empty());

        assertEquals(400, assertThrows(CustomException.class,
                () -> service.resolveCurrency("USD")).getCode());
    }
}
