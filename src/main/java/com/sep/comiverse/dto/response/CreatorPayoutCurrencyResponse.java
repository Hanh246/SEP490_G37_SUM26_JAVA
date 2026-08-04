package com.sep.comiverse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorPayoutCurrencyResponse {
    private String currencyCode;
    private String displayName;
    private String symbol;
    private BigDecimal unitsPerUsd;
    private Boolean active;
}
