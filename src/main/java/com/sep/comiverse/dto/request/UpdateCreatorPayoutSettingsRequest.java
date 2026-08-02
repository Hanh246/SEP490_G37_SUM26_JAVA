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

    private static final String MAX_MONEY = "1000000000000";
    private static final long MAX_UNIT = 1_000_000_000L;

    @NotNull
    @DecimalMin(value = "0", message = "Minimum payout cannot be negative")
    @DecimalMax(value = MAX_MONEY, message = "Minimum payout cannot exceed 1,000,000,000,000 VND")
    @Digits(integer = 13, fraction = 0, message = "Minimum payout must be a whole VND amount")
    private BigDecimal minimumPayoutVnd;

    @NotNull
    @DecimalMin(value = "1", message = "Translator task rate must be at least 1 VND")
    @DecimalMax(value = MAX_MONEY, message = "Translator task rate cannot exceed 1,000,000,000,000 VND")
    @Digits(integer = 13, fraction = 0, message = "Translator task rate must be a whole VND amount")
    private BigDecimal translatorTaskRateVnd;

    @NotNull
    @DecimalMin(value = "1", message = "Translator monthly limit must be at least 1 VND")
    @DecimalMax(value = MAX_MONEY, message = "Translator monthly limit cannot exceed 1,000,000,000,000 VND")
    @Digits(integer = 13, fraction = 0, message = "Translator monthly limit must be a whole VND amount")
    private BigDecimal translatorMonthlyLimitVnd;

    @NotNull
    @Min(value = 1, message = "Author views per unit must be at least 1")
    @Max(value = MAX_UNIT, message = "Author views per unit cannot exceed 1,000,000,000")
    private Long authorViewsPerUnit;

    @NotNull
    @DecimalMin(value = "1", message = "Author view reward must be at least 1 VND")
    @DecimalMax(value = MAX_MONEY, message = "Author view reward cannot exceed 1,000,000,000,000 VND")
    @Digits(integer = 13, fraction = 0, message = "Author view reward must be a whole VND amount")
    private BigDecimal authorViewUnitRateVnd;

    @NotNull
    @Min(value = 1, message = "Author follows per unit must be at least 1")
    @Max(value = MAX_UNIT, message = "Author follows per unit cannot exceed 1,000,000,000")
    private Long authorFollowsPerUnit;

    @NotNull
    @DecimalMin(value = "1", message = "Author follow reward must be at least 1 VND")
    @DecimalMax(value = MAX_MONEY, message = "Author follow reward cannot exceed 1,000,000,000,000 VND")
    @Digits(integer = 13, fraction = 0, message = "Author follow reward must be a whole VND amount")
    private BigDecimal authorFollowUnitRateVnd;

    @NotNull
    @DecimalMin(value = "1", message = "Author monthly limit must be at least 1 VND")
    @DecimalMax(value = MAX_MONEY, message = "Author monthly limit cannot exceed 1,000,000,000,000 VND")
    @Digits(integer = 13, fraction = 0, message = "Author monthly limit must be a whole VND amount")
    private BigDecimal authorMonthlyLimitVnd;
}