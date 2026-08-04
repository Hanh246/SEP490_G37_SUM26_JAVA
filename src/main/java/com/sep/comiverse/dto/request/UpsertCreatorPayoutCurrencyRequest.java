package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpsertCreatorPayoutCurrencyRequest {

    @NotBlank(message = "Currency code is required")
    @Pattern(
            regexp = "^(USD|EUR|CNY)$",
            flags = Pattern.Flag.CASE_INSENSITIVE,
            message = "Currency must be USD, EUR, or CNY"
    )
    private String currencyCode;

    @NotNull(message = "Units per USD is required")
    @DecimalMin(value = "0.000001", message = "Units per USD must be greater than zero")
    @DecimalMax(value = "1000000.000000", message = "Units per USD is too large")
    @Digits(integer = 7, fraction = 6, message = "Units per USD must have at most 6 decimal places")
    private BigDecimal unitsPerUsd;

    @NotNull(message = "Active status is required")
    private Boolean active;
}
