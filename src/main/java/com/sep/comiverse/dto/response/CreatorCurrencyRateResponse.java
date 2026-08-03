package com.sep.comiverse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CreatorCurrencyRateResponse {
    private UUID id;
    private String countryCode;
    private String currencyCode;
    private BigDecimal vndPerUnit;
    private Boolean active;
}
