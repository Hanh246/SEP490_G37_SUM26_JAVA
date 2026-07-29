package com.sep.comiverse.dto.response;

import com.sep.comiverse.entity.enums.BillingInterval;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlanResponse {
    private UUID id;
    private String code;
    private String name;
    private String description;
    private BigDecimal price;
    private String currency;
    private BillingInterval billingInterval;
    private Integer intervalCount;
    private Boolean active;
    private Boolean recommended;
    private String badge;
    private List<String> features;
    private Integer sortOrder;
}
