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
import org.springframework.test.util.ReflectionTestUtils;

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
        ReflectionTestUtils.setField(service, "defaultEurUnitsPerUsd", new BigDecimal("0.920000"));
        ReflectionTestUtils.setField(service, "defaultCnyUnitsPerUsd", new BigDecimal("7.200000"));
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
    void conversion_roundTripsUsingResolvedRate() {
        var eur = new CreatorPayoutSettingsService.ResolvedCurrency(
                CreatorPayoutCurrency.EUR, new BigDecimal("0.920000"));

        BigDecimal eurAmount = service.convertUsdToCurrency(new BigDecimal("100.00"), eur);
        BigDecimal usdAmount = service.convertCurrencyToUsd(eurAmount, eur);

        assertEquals(new BigDecimal("92.00"), eurAmount);
        assertEquals(new BigDecimal("100.00"), usdAmount);
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
    void upsertCurrency_rejectsUnknownCodeAndNonPositiveRate() {
        UpsertCreatorPayoutCurrencyRequest invalidCode = new UpsertCreatorPayoutCurrencyRequest();
        invalidCode.setCurrencyCode("BTC");
        invalidCode.setUnitsPerUsd(BigDecimal.ONE);
        invalidCode.setActive(true);
        assertEquals(400, assertThrows(CustomException.class,
                () -> service.upsertCurrency(invalidCode)).getCode());

        UpsertCreatorPayoutCurrencyRequest zero = new UpsertCreatorPayoutCurrencyRequest();
        zero.setCurrencyCode("EUR");
        zero.setUnitsPerUsd(BigDecimal.ZERO);
        zero.setActive(true);
        assertEquals(400, assertThrows(CustomException.class,
                () -> service.upsertCurrency(zero)).getCode());
    }

    @Test
    void resolveCurrency_returnsActiveRateAndRejectsDisabledOrMissing() {
        CreatorPayoutCurrencyEntity eur = CreatorPayoutCurrencyEntity.builder()
                .currencyCode("EUR").displayName("Euro").symbol("€")
                .unitsPerUsd(new BigDecimal("0.92")).active(true).build();
        when(currencyRepository.findByCurrencyCodeAndDeletedFalse("EUR")).thenReturn(Optional.of(eur));

        var resolved = service.resolveCurrency("eur");
        assertEquals("EUR", resolved.code());
        assertEquals(new BigDecimal("0.920000"), resolved.unitsPerUsd());

        eur.setActive(false);
        assertEquals(400, assertThrows(CustomException.class, () -> service.resolveCurrency("EUR")).getCode());

        when(currencyRepository.findByCurrencyCodeAndDeletedFalse("CNY")).thenReturn(Optional.empty());
        assertEquals(400, assertThrows(CustomException.class, () -> service.resolveCurrency("CNY")).getCode());
    }
}
