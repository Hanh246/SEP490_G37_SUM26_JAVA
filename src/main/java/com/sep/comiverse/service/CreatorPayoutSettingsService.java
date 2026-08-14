package com.sep.comiverse.service;

import com.sep.comiverse.dto.request.UpdateCreatorPayoutSettingsRequest;
import com.sep.comiverse.dto.request.UpsertCreatorPayoutCurrencyRequest;
import com.sep.comiverse.dto.response.CreatorPayoutCurrencyResponse;
import com.sep.comiverse.dto.response.CreatorPayoutSettingsResponse;
import com.sep.comiverse.entity.CreatorPayoutCurrencyEntity;
import com.sep.comiverse.entity.CreatorPayoutSettingEntity;
import com.sep.comiverse.entity.enums.CreatorPayoutCurrency;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.ICreatorPayoutCurrencyRepository;
import com.sep.comiverse.repository.ICreatorPayoutSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreatorPayoutSettingsService {

    public static final String ACCOUNTING_CURRENCY = "USD";
    private static final String DEFAULT_KEY = "DEFAULT";

    private final ICreatorPayoutSettingRepository settingRepository;
    private final ICreatorPayoutCurrencyRepository currencyRateRepository;
    private final JdbcTemplate jdbcTemplate;

    @Value("${payout.currency.eur-units-per-usd:0.920000}")
    private BigDecimal defaultEurUnitsPerUsd;

    @Value("${payout.currency.cny-units-per-usd:7.200000}")
    private BigDecimal defaultCnyUnitsPerUsd;

    @Transactional
    public CreatorPayoutSettingsResponse getSettings() {
        CreatorPayoutSettingEntity settings = getOrCreateSettings();
        ensureDefaultCurrencies();
        return toResponse(settings);
    }

    @Transactional(readOnly = true)
    public CreatorPayoutSettingEntity currentSettings() {
        return settingRepository.findByConfigKeyAndDeletedFalse(DEFAULT_KEY)
                .orElseGet(this::defaultSettingsDetached);
    }

    @Transactional
    public CreatorPayoutSettingsResponse updateSettings(
            UpdateCreatorPayoutSettingsRequest request
    ) {
        CreatorPayoutSettingEntity settings = getOrCreateSettings();
        settings.setMinimumPayoutUsd(normalizeMoney(request.getMinimumPayoutUsd()));
        settings.setTranslatorTaskRateUsd(normalizeMoney(request.getTranslatorTaskRateUsd()));
        settings.setTranslatorMonthlyLimitUsd(normalizeMoney(request.getTranslatorMonthlyLimitUsd()));
        settings.setAuthorViewsPerUnit(request.getAuthorViewsPerUnit());
        settings.setAuthorViewUnitRateUsd(normalizeMoney(request.getAuthorViewUnitRateUsd()));
        settings.setAuthorFollowsPerUnit(request.getAuthorFollowsPerUnit());
        settings.setAuthorFollowUnitRateUsd(normalizeMoney(request.getAuthorFollowUnitRateUsd()));
        settings.setAuthorMonthlyLimitUsd(normalizeMoney(request.getAuthorMonthlyLimitUsd()));
        settings.setCurrency(ACCOUNTING_CURRENCY);
        settings.setDeleted(false);
        ensureDefaultCurrencies();
        return toResponse(settingRepository.save(settings));
    }

    @Transactional
    public CreatorPayoutCurrencyResponse upsertCurrency(
            UpsertCreatorPayoutCurrencyRequest request
    ) {
        CreatorPayoutCurrency currency;
        try {
            currency = CreatorPayoutCurrency.fromCode(request.getCurrencyCode());
        } catch (IllegalArgumentException ex) {
            throw new CustomException(400, ex.getMessage(), HttpStatus.BAD_REQUEST);
        }

        boolean active = Boolean.TRUE.equals(request.getActive());
        BigDecimal unitsPerUsd = normalizeRate(request.getUnitsPerUsd());

        if (currency == CreatorPayoutCurrency.USD) {
            unitsPerUsd = BigDecimal.ONE.setScale(6, RoundingMode.UNNECESSARY);
            active = true;
        }

        CreatorPayoutCurrencyEntity entity = currencyRateRepository
                .findByCurrencyCode(currency.getCode())
                .orElseGet(CreatorPayoutCurrencyEntity::new);

        entity.setCurrencyCode(currency.getCode());
        entity.setDisplayName(currency.getDisplayName());
        entity.setSymbol(currency.getSymbol());
        entity.setUnitsPerUsd(unitsPerUsd);
        entity.setActive(active);
        entity.setDeleted(false);

        return toCurrencyResponse(currencyRateRepository.save(entity));
    }

    @Transactional
    public List<CreatorPayoutCurrencyResponse> getSupportedCurrencies() {
        ensureDefaultCurrencies();
        return currencyRateRepository
                .findAllByDeletedFalseOrderByCurrencyCodeAsc()
                .stream()
                .filter(item -> Boolean.TRUE.equals(item.getActive()))
                .map(this::toCurrencyResponse)
                .sorted(Comparator.comparingInt(this::currencyOrder))
                .toList();
    }

    @Transactional
    public ResolvedCurrency resolveCurrency(String code) {
        ensureDefaultCurrencies();

        CreatorPayoutCurrency currency;
        try {
            currency = CreatorPayoutCurrency.fromCode(code);
        } catch (IllegalArgumentException ex) {
            throw new CustomException(400, ex.getMessage(), HttpStatus.BAD_REQUEST);
        }

        CreatorPayoutCurrencyEntity rate = currencyRateRepository
                .findByCurrencyCodeAndDeletedFalse(currency.getCode())
                .orElseThrow(() -> new CustomException(
                        400,
                        "Payout currency is not configured: " + currency.getCode(),
                        HttpStatus.BAD_REQUEST
                ));

        if (!Boolean.TRUE.equals(rate.getActive())) {
            throw new CustomException(
                    400,
                    "Payout currency is currently disabled: " + currency.getCode(),
                    HttpStatus.BAD_REQUEST
            );
        }

        return new ResolvedCurrency(
                currency,
                normalizeRate(rate.getUnitsPerUsd())
        );
    }

    public BigDecimal convertUsdToCurrency(
            BigDecimal usdAmount,
            ResolvedCurrency currency
    ) {
        return normalizeMoney(
                normalizeMoney(usdAmount).multiply(currency.unitsPerUsd())
        );
    }

    public BigDecimal convertCurrencyToUsd(
            BigDecimal currencyAmount,
            ResolvedCurrency currency
    ) {
        if (currency.unitsPerUsd().signum() <= 0) {
            throw new CustomException(
                    500,
                    "Invalid payout currency rate",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
        return normalizeMoney(
                normalizeMoney(currencyAmount).divide(
                        currency.unitsPerUsd(),
                        8,
                        RoundingMode.HALF_UP
                )
        );
    }

    public BigDecimal normalizeMoney(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal normalizeRate(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new CustomException(
                    400,
                    "Currency rate must be greater than zero",
                    HttpStatus.BAD_REQUEST
            );
        }
        return value.setScale(6, RoundingMode.HALF_UP);
    }

    private CreatorPayoutSettingEntity getOrCreateSettings() {
        return settingRepository.findByConfigKey(DEFAULT_KEY)
                .map(existing -> {
                    existing.setDeleted(false);
                    existing.setCurrency(ACCOUNTING_CURRENCY);
                    return settingRepository.save(existing);
                })
                .orElseGet(() -> settingRepository.save(defaultSettingsDetached()));
    }

    private void ensureDefaultCurrencies() {
        upsertDefaultCurrency(
                CreatorPayoutCurrency.USD,
                BigDecimal.ONE
        );
        upsertDefaultCurrency(
                CreatorPayoutCurrency.EUR,
                defaultEurUnitsPerUsd
        );
        upsertDefaultCurrency(
                CreatorPayoutCurrency.CNY,
                defaultCnyUnitsPerUsd
        );
    }

    private void upsertDefaultCurrency(
            CreatorPayoutCurrency currency,
            BigDecimal defaultRate
    ) {
        BigDecimal rate = currency == CreatorPayoutCurrency.USD
                ? BigDecimal.ONE.setScale(6)
                : normalizeRate(defaultRate);

        CreatorPayoutCurrencyEntity entity = currencyRateRepository
                .findByCurrencyCode(currency.getCode())
                .orElseGet(() -> CreatorPayoutCurrencyEntity.builder()
                        .currencyCode(currency.getCode())
                        .displayName(currency.getDisplayName())
                        .symbol(currency.getSymbol())
                        .unitsPerUsd(rate)
                        .active(true)
                        .build());

        entity.setDisplayName(currency.getDisplayName());
        entity.setSymbol(currency.getSymbol());
        if (currency == CreatorPayoutCurrency.USD) {
            entity.setUnitsPerUsd(BigDecimal.ONE.setScale(6, RoundingMode.UNNECESSARY));
            entity.setActive(true);
        }
        entity.setDeleted(false);
        currencyRateRepository.save(entity);
    }

    private CreatorPayoutSettingEntity defaultSettingsDetached() {
        return CreatorPayoutSettingEntity.builder()
                .configKey(DEFAULT_KEY)
                .currency(ACCOUNTING_CURRENCY)
                .build();
    }

    private CreatorPayoutSettingsResponse toResponse(
            CreatorPayoutSettingEntity settings
    ) {
        return CreatorPayoutSettingsResponse.builder()
                .accountingCurrency(ACCOUNTING_CURRENCY)
                .minimumPayoutUsd(normalizeMoney(settings.getMinimumPayoutUsd()))
                .translatorTaskRateUsd(normalizeMoney(settings.getTranslatorTaskRateUsd()))
                .translatorMonthlyLimitUsd(normalizeMoney(settings.getTranslatorMonthlyLimitUsd()))
                .authorViewsPerUnit(settings.getAuthorViewsPerUnit())
                .authorViewUnitRateUsd(normalizeMoney(settings.getAuthorViewUnitRateUsd()))
                .authorFollowsPerUnit(settings.getAuthorFollowsPerUnit())
                .authorFollowUnitRateUsd(normalizeMoney(settings.getAuthorFollowUnitRateUsd()))
                .authorMonthlyLimitUsd(normalizeMoney(settings.getAuthorMonthlyLimitUsd()))
                .updatedAt(settings.getUpdatedAt())
                .supportedCurrencies(currencyRateRepository
                        .findAllByDeletedFalseOrderByCurrencyCodeAsc()
                        .stream()
                        .map(this::toCurrencyResponse)
                        .sorted(Comparator.comparingInt(this::currencyOrder))
                        .toList())
                .build();
    }

    private CreatorPayoutCurrencyResponse toCurrencyResponse(
            CreatorPayoutCurrencyEntity entity
    ) {
        return CreatorPayoutCurrencyResponse.builder()
                .currencyCode(entity.getCurrencyCode())
                .displayName(entity.getDisplayName())
                .symbol(entity.getSymbol())
                .unitsPerUsd(entity.getUnitsPerUsd() == null
                        ? null
                        : entity.getUnitsPerUsd().setScale(6, RoundingMode.HALF_UP))
                .active(entity.getActive())
                .build();
    }

    private int currencyOrder(CreatorPayoutCurrencyResponse item) {
        return switch (item.getCurrencyCode()) {
            case "USD" -> 0;
            case "EUR" -> 1;
            case "CNY" -> 2;
            default -> 99;
        };
    }

    public record ResolvedCurrency(
            CreatorPayoutCurrency currency,
            BigDecimal unitsPerUsd
    ) {
        public String code() {
            return currency.getCode();
        }

        public String symbol() {
            return currency.getSymbol();
        }
    }
}
