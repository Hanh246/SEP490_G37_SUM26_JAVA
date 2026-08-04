package com.sep.comiverse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StripePayoutOnboardingLinkResponse {
    private String onboardingUrl;
    private Instant expiresAt;
    private CreatorPayoutAccountResponse account;
}
