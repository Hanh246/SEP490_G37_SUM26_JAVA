package com.sep.comiverse.service;

import com.sep.comiverse.dto.request.UpdateCreatorPayoutSettingsRequest;
import com.sep.comiverse.dto.request.UpsertCreatorCurrencyRateRequest;
import com.sep.comiverse.dto.response.CreatorCurrencyRateResponse;
import com.sep.comiverse.dto.response.CreatorPayoutSettingsResponse;
import com.sep.comiverse.entity.CreatorPayoutCurrencyRateEntity;
import com.sep.comiverse.entity.CreatorPayoutSettingEntity;
import com.sep.comiverse.repository.ICreatorPayoutCurrencyRateRepository;
import com.sep.comiverse.repository.ICreatorPayoutSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreatorPayoutSettingsService {

    private static final String DEFAULT_KEY = "DEFAULT";
    private static final Map<String, CurrencySeed> DEFAULT_CURRENCIES = Map.of(
            "VN", new CurrencySeed("VND", BigDecimal.ONE),
            "US", new CurrencySeed("USD", BigDecimal.valueOf(25_000)),
            "GB", new CurrencySeed("GBP", BigDecimal.valueOf(32_000)),
            "JP", new CurrencySeed("JPY", BigDecimal.valueOf(170)),
            "KR", new CurrencySeed("KRW", BigDecimal.valueOf(19))
    );

    private final ICreatorPayoutSettingRepository settingRepository;
    private final ICreatorPayoutCurrencyRateRepository currencyRateRepository;
    private final JdbcTemplate jdbcTemplate;

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
    public CreatorPayoutSettingsResponse updateSettings(UpdateCreatorPayoutSettingsRequest request) {
        CreatorPayoutSettingEntity settings = getOrCreateSettings();
        settings.setMinimumPayoutVnd(request.getMinimumPayoutVnd());
        settings.setTranslatorTaskRateVnd(request.getTranslatorTaskRateVnd());
        settings.setTranslatorMonthlyLimitVnd(request.getTranslatorMonthlyLimitVnd());
        settings.setAuthorViewsPerUnit(request.getAuthorViewsPerUnit());
        settings.setAuthorViewUnitRateVnd(request.getAuthorViewUnitRateVnd());
        settings.setAuthorFollowsPerUnit(request.getAuthorFollowsPerUnit());
        settings.setAuthorFollowUnitRateVnd(request.getAuthorFollowUnitRateVnd());
        settings.setAuthorMonthlyLimitVnd(request.getAuthorMonthlyLimitVnd());
        settings.setDeleted(false);
        CreatorPayoutSettingEntity saved = settingRepository.save(settings);
        ensureDefaultCurrencies();
        return toResponse(saved);
    }

    @Transactional
    public CreatorCurrencyRateResponse upsertCurrency(UpsertCreatorCurrencyRateRequest request) {
        String country = request.getCountryCode().trim().toUpperCase(Locale.ROOT);
        String currency = request.getCurrencyCode().trim().toUpperCase(Locale.ROOT);
        CreatorPayoutCurrencyRateEntity entity = currencyRateRepository
                .findByCountryCodeIgnoreCaseAndDeletedFalse(country)
                .orElseGet(CreatorPayoutCurrencyRateEntity::new);
        entity.setCountryCode(country);
        entity.setCurrencyCode(currency);
        entity.setVndPerUnit(request.getVndPerUnit());
        entity.setActive(request.getActive() == null || request.getActive());
        entity.setDeleted(false);
        return toCurrencyResponse(currencyRateRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public ResolvedCurrency resolveCurrency(String countryCode) {
        String country = normalizeCountry(countryCode);
        var configured = currencyRateRepository.findByCountryCodeIgnoreCaseAndDeletedFalse(country);
        if (configured.isPresent() && Boolean.TRUE.equals(configured.get().getActive())) {
            CreatorPayoutCurrencyRateEntity rate = configured.get();
            return new ResolvedCurrency(country, rate.getCurrencyCode(), rate.getVndPerUnit());
        }

        // Only use a built-in sandbox seed when Admin has never created a mapping.
        // An explicitly disabled mapping must stay disabled and fall back to VND.
        if (configured.isEmpty()) {
            CurrencySeed seed = DEFAULT_CURRENCIES.get(country);
            if (seed != null) {
                return new ResolvedCurrency(country, seed.currencyCode(), seed.vndPerUnit());
            }
        }

        CreatorPayoutCurrencyRateEntity vndRate = currencyRateRepository
                .findByCountryCodeIgnoreCaseAndDeletedFalse("VN")
                .filter(item -> Boolean.TRUE.equals(item.getActive()))
                .orElse(null);
        return new ResolvedCurrency(
                country,
                vndRate == null ? "VND" : vndRate.getCurrencyCode(),
                vndRate == null ? BigDecimal.ONE : vndRate.getVndPerUnit()
        );
    }

    public BigDecimal convertFromVnd(BigDecimal amountVnd, ResolvedCurrency currency) {
        BigDecimal safe = amountVnd == null ? BigDecimal.ZERO : amountVnd;
        BigDecimal rate = currency == null || currency.vndPerUnit() == null || currency.vndPerUnit().signum() <= 0
                ? BigDecimal.ONE : currency.vndPerUnit();
        int scale = isZeroDecimal(currency == null ? "VND" : currency.currencyCode()) ? 0 : 2;
        return safe.divide(rate, scale + 4, RoundingMode.HALF_UP).setScale(scale, RoundingMode.HALF_UP);
    }

    private boolean isZeroDecimal(String currency) {
        return List.of("BIF", "CLP", "DJF", "GNF", "JPY", "KMF", "KRW", "MGA", "PYG", "RWF", "UGX", "VND", "VUV", "XAF", "XOF", "XPF")
                .contains(currency == null ? "VND" : currency.toUpperCase(Locale.ROOT));
    }

    /**
     * Creates the singleton settings row without a read-then-insert race.
     *
     * The unique key is global, while the old lookup ignored soft-deleted rows.
     * That allowed an existing DEFAULT row to be missed and inserted again.
     * PostgreSQL ON CONFLICT makes this operation safe for concurrent requests
     * and reactivates an older soft-deleted DEFAULT row.
     */
    private CreatorPayoutSettingEntity getOrCreateSettings() {
        CreatorPayoutSettingEntity defaults = defaultSettingsDetached();

        jdbcTemplate.update("""
                INSERT INTO creator_payout_settings (
                    id,
                    config_key,
                    minimum_payout_vnd,
                    translator_task_rate_vnd,
                    translator_monthly_limit_vnd,
                    author_views_per_unit,
                    author_view_unit_rate_vnd,
                    author_follows_per_unit,
                    author_follow_unit_rate_vnd,
                    author_monthly_limit_vnd,
                    deleted,
                    create_at,
                    update_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (config_key) DO UPDATE
                SET deleted = false,
                    update_at = CURRENT_TIMESTAMP
                """,
                UUID.randomUUID(),
                DEFAULT_KEY,
                defaults.getMinimumPayoutVnd(),
                defaults.getTranslatorTaskRateVnd(),
                defaults.getTranslatorMonthlyLimitVnd(),
                defaults.getAuthorViewsPerUnit(),
                defaults.getAuthorViewUnitRateVnd(),
                defaults.getAuthorFollowsPerUnit(),
                defaults.getAuthorFollowUnitRateVnd(),
                defaults.getAuthorMonthlyLimitVnd()
        );

        return settingRepository.findByConfigKey(DEFAULT_KEY)
                .orElseThrow(() -> new IllegalStateException(
                        "Could not create or load the default creator payout settings"
                ));
    }

    private CreatorPayoutSettingEntity defaultSettingsDetached() {
        return CreatorPayoutSettingEntity.builder().configKey(DEFAULT_KEY).build();
    }

    private void ensureDefaultCurrencies() {
        for (Map.Entry<String, CurrencySeed> entry : DEFAULT_CURRENCIES.entrySet()) {
            CurrencySeed seed = entry.getValue();

            // Seed only when the country has never been configured. Do not
            // overwrite an Admin rate or reactivate a deliberately disabled row.
            jdbcTemplate.update("""
                    INSERT INTO creator_payout_currency_rates (
                        id,
                        country_code,
                        currency_code,
                        vnd_per_unit,
                        active,
                        deleted,
                        create_at,
                        update_at
                    ) VALUES (?, ?, ?, ?, true, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    ON CONFLICT (country_code) DO NOTHING
                    """,
                    UUID.randomUUID(),
                    entry.getKey(),
                    seed.currencyCode(),
                    seed.vndPerUnit()
            );
        }
    }

    private String normalizeCountry(String value) {
        if (value == null || !value.trim().matches("^[A-Za-z]{2}$")) return "VN";
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private CreatorPayoutSettingsResponse toResponse(CreatorPayoutSettingEntity settings) {
        return CreatorPayoutSettingsResponse.builder()
                .minimumPayoutVnd(settings.getMinimumPayoutVnd())
                .translatorTaskRateVnd(settings.getTranslatorTaskRateVnd())
                .translatorMonthlyLimitVnd(settings.getTranslatorMonthlyLimitVnd())
                .authorViewsPerUnit(settings.getAuthorViewsPerUnit())
                .authorViewUnitRateVnd(settings.getAuthorViewUnitRateVnd())
                .authorFollowsPerUnit(settings.getAuthorFollowsPerUnit())
                .authorFollowUnitRateVnd(settings.getAuthorFollowUnitRateVnd())
                .authorMonthlyLimitVnd(settings.getAuthorMonthlyLimitVnd())
                .updatedAt(settings.getUpdatedAt())
                .currencyRates(currencyRateRepository.findAllByDeletedFalseOrderByCountryCodeAsc().stream()
                        .map(this::toCurrencyResponse).toList())
                .build();
    }

    private CreatorCurrencyRateResponse toCurrencyResponse(CreatorPayoutCurrencyRateEntity item) {
        return CreatorCurrencyRateResponse.builder()
                .id(item.getId())
                .countryCode(item.getCountryCode())
                .currencyCode(item.getCurrencyCode())
                .vndPerUnit(item.getVndPerUnit())
                .active(item.getActive())
                .build();
    }

    private record CurrencySeed(String currencyCode, BigDecimal vndPerUnit) {}
    public record ResolvedCurrency(String countryCode, String currencyCode, BigDecimal vndPerUnit) {}
}