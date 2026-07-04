package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class UpdatePremiumPlanSettingsRequest {
    @NotNull(message = "Monthly price is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Monthly price cannot be negative")
    private BigDecimal monthlyPrice;

    @NotNull(message = "Yearly price is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Yearly price cannot be negative")
    private BigDecimal yearlyPrice;

    @NotEmpty(message = "Premium benefits cannot be empty")
    @Size(max = 12, message = "Premium benefits can contain at most 12 items")
    private List<@Size(max = 140, message = "Each benefit must be at most 140 characters") String> benefits;
}
