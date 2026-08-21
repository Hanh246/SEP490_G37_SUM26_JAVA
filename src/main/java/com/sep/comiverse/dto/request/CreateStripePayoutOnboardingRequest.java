package com.sep.comiverse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateStripePayoutOnboardingRequest {

    @Pattern(
            regexp = "^[A-Za-z]{2}$",
            message = "Country code must contain exactly two letters"
    )
    private String countryCode;

    @NotBlank(message = "Payout currency is required")
    @Pattern(
            regexp = "^USD$",
            flags = Pattern.Flag.CASE_INSENSITIVE,
            message = "Payout currency must be USD"
    )
    private String payoutCurrency;
}
