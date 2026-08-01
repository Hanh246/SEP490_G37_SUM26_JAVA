package com.sep.comiverse.dto.response;

import com.sep.comiverse.entity.enums.CreatorPayoutRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
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
    private Boolean payoutsEnabled;
    private Boolean active;
    private Instant verifiedAt;
}
