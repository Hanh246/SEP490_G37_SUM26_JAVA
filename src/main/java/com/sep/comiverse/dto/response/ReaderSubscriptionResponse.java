package com.sep.comiverse.dto.response;

import com.sep.comiverse.entity.enums.ReaderSubscriptionStatus;
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
public class ReaderSubscriptionResponse {
    private UUID id;
    private UUID planId;
    private String planCode;
    private String planName;
    private ReaderSubscriptionStatus status;
    private Instant currentPeriodStart;
    private Instant currentPeriodEnd;
    private Boolean cancelAtPeriodEnd;
    private Boolean premiumActive;
    private Boolean requiresBillingManagement;
    private Boolean billingPortalAvailable;
}
