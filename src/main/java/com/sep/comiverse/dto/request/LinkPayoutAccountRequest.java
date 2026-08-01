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
public class LinkPayoutAccountRequest {

    @NotBlank(message = "Stripe connected account ID is required")
    @Pattern(regexp = "^acct_[A-Za-z0-9]+$", message = "Stripe connected account ID must start with acct_")
    private String stripeConnectedAccountId;
}
