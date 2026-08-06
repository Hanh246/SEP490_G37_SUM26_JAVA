package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateCreatorPayoutSettingsRequest {

    private static final String MAX_USD = "1000000.00";
    private static final long MAX_UNIT = 1_000_000_000L;

    @NotNull
    @DecimalMin(value = "0.00", message = "Minimum payout cannot be negative")
    @DecimalMax(value = MAX_USD, message = "Minimum payout cannot exceed 1,000,000 USD")
    @Digits(integer = 7, fraction = 2, message = "Minimum payout must have at most 2 decimal places")
    private BigDecimal minimumPayoutUsd;

    @NotNull
    @DecimalMin(value = "0.01", message = "Translator page rate must be at least 0.01 USD")
    @DecimalMax(value = MAX_USD, message = "Translator page rate cannot exceed 1,000,000 USD")
    @Digits(integer = 7, fraction = 2, message = "Translator page rate must have at most 2 decimal places")
    private BigDecimal translatorTaskRateUsd;

    @NotNull
    @DecimalMin(value = "0.01", message = "Translator monthly limit must be at least 0.01 USD")
    @DecimalMax(value = MAX_USD, message = "Translator monthly limit cannot exceed 1,000,000 USD")
    @Digits(integer = 7, fraction = 2, message = "Translator monthly limit must have at most 2 decimal places")
    private BigDecimal translatorMonthlyLimitUsd;

    @NotNull
    @Min(value = 1, message = "Author views per unit must be at least 1")
    @Max(value = MAX_UNIT, message = "Author views per unit cannot exceed 1,000,000,000")
    private Long authorViewsPerUnit;

    @NotNull
    @DecimalMin(value = "0.01", message = "Author view reward must be at least 0.01 USD")
    @DecimalMax(value = MAX_USD, message = "Author view reward cannot exceed 1,000,000 USD")
    @Digits(integer = 7, fraction = 2, message = "Author view reward must have at most 2 decimal places")
    private BigDecimal authorViewUnitRateUsd;

    @NotNull
    @Min(value = 1, message = "Author follows per unit must be at least 1")
    @Max(value = MAX_UNIT, message = "Author follows per unit cannot exceed 1,000,000,000")
    private Long authorFollowsPerUnit;

    @NotNull
    @DecimalMin(value = "0.01", message = "Author follow reward must be at least 0.01 USD")
    @DecimalMax(value = MAX_USD, message = "Author follow reward cannot exceed 1,000,000 USD")
    @Digits(integer = 7, fraction = 2, message = "Author follow reward must have at most 2 decimal places")
    private BigDecimal authorFollowUnitRateUsd;

    @NotNull
    @DecimalMin(value = "0.01", message = "Author monthly limit must be at least 0.01 USD")
    @DecimalMax(value = MAX_USD, message = "Author monthly limit cannot exceed 1,000,000 USD")
    @Digits(integer = 7, fraction = 2, message = "Author monthly limit must have at most 2 decimal places")
    private BigDecimal authorMonthlyLimitUsd;
}
