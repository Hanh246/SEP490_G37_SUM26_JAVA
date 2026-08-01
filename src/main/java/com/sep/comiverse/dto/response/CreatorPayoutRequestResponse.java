package com.sep.comiverse.dto.response;

import com.sep.comiverse.entity.enums.CreatorPayoutRole;
import com.sep.comiverse.entity.enums.CreatorPayoutStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorPayoutRequestResponse {
    private UUID id;
    private UUID userId;
    private String userName;
    private String userEmail;
    private CreatorPayoutRole role;
    private String payoutMonth;
    private BigDecimal amount;
    private String currency;
    private CreatorPayoutStatus status;
    private String stripeConnectedAccountId;
    private String stripeTransferId;
    private String requestNote;
    private String adminNote;
    private String failureReason;
    private String calculationDetails;
    private Instant requestedAt;
    private Instant approvedAt;
    private Instant paidAt;
    private Instant rejectedAt;
    private Instant failedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
