package com.sep.comiverse.dto.response;

import com.sep.comiverse.entity.enums.CreatorPayoutRole;
import com.sep.comiverse.entity.enums.StripePayoutProfileStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorPayoutAccountResponse {
    private UUID id;
    private UUID userId;
    private CreatorPayoutRole role;
    private String stripeConnectedAccountId;
    private String currency;
    private String accountCountry;
    private String transfersCapability;
    private Boolean detailsSubmitted;
    private Boolean chargesEnabled;
    private Boolean payoutsEnabled;
    private Boolean readyForPayout;
    private Boolean active;
    private StripePayoutProfileStatus onboardingStatus;
    private List<String> requirementsCurrentlyDue;
    private String requirementsDisabledReason;
    private String externalAccountType;
    private String externalAccountLast4;
    private String externalAccountDisplayName;
    private Instant verifiedAt;
    private Instant lastSyncedAt;
    private Instant onboardingCompletedAt;
}
