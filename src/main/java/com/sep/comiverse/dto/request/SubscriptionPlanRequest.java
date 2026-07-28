package com.sep.comiverse.dto.request;

import com.sep.comiverse.entity.enums.BillingInterval;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlanRequest {

    @NotBlank(message = "Plan code is required")
    @Pattern(regexp = "^[A-Za-z0-9_-]{2,50}$", message = "Plan code may contain only letters, numbers, hyphens, and underscores")
    private String code;

    @NotBlank(message = "Plan name is required")
    @Size(max = 120, message = "Plan name must be at most 120 characters")
    private String name;

    @Size(max = 500, message = "Description must be at most 500 characters")
    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    private BigDecimal price;

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Za-z]{3}$", message = "Currency must be a 3-letter ISO code")
    private String currency;

    @NotNull(message = "Billing interval is required")
    private BillingInterval billingInterval;

    @NotNull(message = "Interval count is required")
    @Min(value = 1, message = "Interval count must be at least 1")
    @Max(value = 12, message = "Interval count must be at most 12")
    private Integer intervalCount;

    private Boolean active;
    private Boolean recommended;

    @Size(max = 80, message = "Badge must be at most 80 characters")
    private String badge;

    @Size(max = 15, message = "A plan can contain at most 15 features")
    private List<@Size(max = 160, message = "Each feature must be at most 160 characters") String> features;

    @Min(value = 0, message = "Sort order cannot be negative")
    @Max(value = 9999, message = "Sort order is too large")
    private Integer sortOrder;
}
