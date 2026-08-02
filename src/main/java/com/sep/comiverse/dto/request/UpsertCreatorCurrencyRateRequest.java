package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpsertCreatorCurrencyRateRequest {
    @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$") private String countryCode;
    @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$") private String currencyCode;
    @NotNull @DecimalMin("0.000001") private BigDecimal vndPerUnit;
    private Boolean active = true;
}
